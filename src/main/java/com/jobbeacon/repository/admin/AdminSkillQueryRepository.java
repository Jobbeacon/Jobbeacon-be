package com.jobbeacon.repository.admin;

import com.jobbeacon.dto.request.admin.AdminPageQuery;
import com.jobbeacon.dto.response.admin.AdminSkillResponse;
import com.jobbeacon.dto.response.admin.AdminSkillStatsResponse;
import com.jobbeacon.utils.UuidUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class AdminSkillQueryRepository {

  /** Created since the bound: v7 ids are time ordered, other versions are excluded. */
  private static final String RECENT = "(s.id >= :recentBound and substring(cast(s.id as text), 15, 1) = '7')";

  private static final String FROM = """
      from skill s
      left join (select skill_id, count(*) as n from job_skill_mapping group by skill_id) jc on jc.skill_id = s.id
      left join (select skill_id, count(*) as n from cv_skill_mapping group by skill_id) cc on cc.skill_id = s.id
      """;

  private final NamedParameterJdbcTemplate jdbc;

  private record Row(UUID id, String name, long jobCount, long cvCount) {
  }

  public AdminSkillStatsResponse stats(Instant recentSince) {
    return jdbc.queryForObject("""
        select count(*) as total,
               count(*) filter (where %s) as recent,
               count(*) filter (where not exists (select 1 from skill_alias a where a.skill_id = s.id)) as without_alias,
               count(*) filter (where not exists (select 1 from job_skill_mapping m where m.skill_id = s.id)) as unused
        from skill s
        """.formatted(RECENT), new MapSqlParameterSource("recentBound", UuidUtils.v7LowerBound(recentSince)),
        (rs, i) -> new AdminSkillStatsResponse(
            rs.getLong("total"), rs.getLong("recent"), rs.getLong("without_alias"), rs.getLong("unused")));
  }

  public List<AdminSkillResponse> find(String q, String filter, String sort, Instant recentSince, AdminPageQuery page) {
    MapSqlParameterSource params = new MapSqlParameterSource()
        .addValue("limit", page.size())
        .addValue("offset", page.offset());
    String sql = "select s.id, s.name, coalesce(jc.n, 0) as job_count, coalesce(cc.n, 0) as cv_count "
        + FROM + where(q, filter, recentSince, params) + " order by " + orderBy(sort)
        + " limit :limit offset :offset";
    List<Row> rows = jdbc.query(sql, params, (rs, i) -> new Row(
        AdminSql.uuid(rs, "id"), rs.getString("name"), rs.getLong("job_count"), rs.getLong("cv_count")));
    Map<UUID, List<String>> aliases = aliases(rows.stream().map(Row::id).toList());
    return rows.stream()
        .map(row -> new AdminSkillResponse(row.id(), row.name(), aliases.getOrDefault(row.id(), List.of()),
            row.jobCount(), row.cvCount(), UuidUtils.timestampOf(row.id())))
        .toList();
  }

  public long count(String q, String filter, Instant recentSince) {
    MapSqlParameterSource params = new MapSqlParameterSource();
    Long total = jdbc.queryForObject("select count(*) " + FROM + where(q, filter, recentSince, params), params,
        Long.class);
    return total == null ? 0 : total;
  }

  public AdminSkillResponse findOne(UUID id) {
    List<Row> rows = jdbc.query("select s.id, s.name, coalesce(jc.n, 0) as job_count, coalesce(cc.n, 0) as cv_count "
            + FROM + " where s.id = :id", new MapSqlParameterSource("id", id),
        (rs, i) -> new Row(AdminSql.uuid(rs, "id"), rs.getString("name"), rs.getLong("job_count"),
            rs.getLong("cv_count")));
    if (rows.isEmpty()) {
      return null;
    }
    Row row = rows.get(0);
    return new AdminSkillResponse(row.id(), row.name(), aliases(List.of(id)).getOrDefault(id, List.of()),
        row.jobCount(), row.cvCount(), UuidUtils.timestampOf(id));
  }

  public Map<UUID, List<String>> aliases(Collection<UUID> skillIds) {
    Map<UUID, List<String>> aliases = new HashMap<>();
    if (skillIds.isEmpty()) {
      return aliases;
    }
    jdbc.query("select skill_id, alias from skill_alias where skill_id in (:ids) order by lower(alias)",
        new MapSqlParameterSource("ids", skillIds),
        rs -> {
          aliases.computeIfAbsent(AdminSql.uuid(rs, "skill_id"), id -> new ArrayList<>()).add(rs.getString("alias"));
        });
    return aliases;
  }

  public List<UUID> jobIds(UUID skillId) {
    return jdbc.queryForList("select job_id from job_skill_mapping where skill_id = :id",
        new MapSqlParameterSource("id", skillId), UUID.class);
  }

  /** Moves job and CV links from one skill to another, dropping links the target already has. */
  public void moveMappings(UUID sourceId, UUID targetId) {
    MapSqlParameterSource params = new MapSqlParameterSource("source", sourceId).addValue("target", targetId);
    jdbc.update("""
        insert into job_skill_mapping (job_id, skill_id)
        select job_id, :target from job_skill_mapping where skill_id = :source
        on conflict do nothing
        """, params);
    jdbc.update("""
        insert into cv_skill_mapping (cv_profile_id, skill_id, confidence, experience_months, last_used_at, evidence_text)
        select cv_profile_id, :target, confidence, experience_months, last_used_at, evidence_text
        from cv_skill_mapping where skill_id = :source
        on conflict do nothing
        """, params);
    deleteMappings(sourceId);
  }

  public void deleteMappings(UUID skillId) {
    MapSqlParameterSource params = new MapSqlParameterSource("id", skillId);
    jdbc.update("delete from job_skill_mapping where skill_id = :id", params);
    jdbc.update("delete from cv_skill_mapping where skill_id = :id", params);
  }

  public void deleteAliases(UUID skillId) {
    jdbc.update("delete from skill_alias where skill_id = :id", new MapSqlParameterSource("id", skillId));
  }

  private static String where(String q, String filter, Instant recentSince, MapSqlParameterSource params) {
    List<String> clauses = new ArrayList<>();
    String pattern = AdminSql.containsPattern(q);
    if (pattern != null) {
      clauses.add("(s.name ilike :q or exists (select 1 from skill_alias a where a.skill_id = s.id and a.alias ilike :q))");
      params.addValue("q", pattern);
    }
    switch (filter == null ? "" : filter.toLowerCase()) {
      case "recent" -> {
        clauses.add(RECENT);
        params.addValue("recentBound", UuidUtils.v7LowerBound(recentSince));
      }
      case "noalias" -> clauses.add("not exists (select 1 from skill_alias a where a.skill_id = s.id)");
      case "unused" -> clauses.add("jc.n is null");
      default -> {
      }
    }
    return clauses.isEmpty() ? "" : " where " + String.join(" and ", clauses);
  }

  private static String orderBy(String sort) {
    return switch (sort == null ? "jobs" : sort.toLowerCase()) {
      case "recent" -> "s.id desc";
      case "name" -> "lower(s.name) asc, s.id asc";
      default -> "job_count desc, lower(s.name) asc, s.id asc";
    };
  }
}
