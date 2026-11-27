package com.jobbeacon.repository.admin;

import com.jobbeacon.dto.request.admin.AdminPageQuery;
import com.jobbeacon.dto.response.admin.AdminCompanyRowResponse;
import com.jobbeacon.dto.response.admin.AdminCompanyStatsResponse;
import com.jobbeacon.utils.UuidUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class AdminCompanyQueryRepository {

  private static final String SELECT = """
      select c.id, c.name, c.logo_url,
             coalesce(jc.job_count, 0) as job_count,
             coalesce(jc.open_count, 0) as open_count,
             src.sources, src.last_crawled_at
      from company c
      left join (
          select company_id, count(*) as job_count,
                 count(*) filter (where expired_date is null or expired_date > now()) as open_count
          from job group by company_id
      ) jc on jc.company_id = c.id
      left join (
          select j.company_id, array_agg(distinct s.domain) as sources, max(m.crawled_at) as last_crawled_at
          from job j
          join job_source_mapping m on m.job_id = j.id
          join job_source s on s.id = m.source_id
          group by j.company_id
      ) src on src.company_id = c.id
      """;

  private static final RowMapper<AdminCompanyRowResponse> ROW = (rs, i) -> {
    UUID id = AdminSql.uuid(rs, "id");
    return new AdminCompanyRowResponse(
        id,
        rs.getString("name"),
        rs.getString("logo_url"),
        rs.getLong("job_count"),
        rs.getLong("open_count"),
        AdminSql.sortedStrings(rs, "sources"),
        UuidUtils.timestampOf(id),
        AdminSql.instant(rs, "last_crawled_at"));
  };

  private final NamedParameterJdbcTemplate jdbc;

  public record CompanyName(UUID id, String name) {
  }

  public AdminCompanyStatsResponse stats() {
    return jdbc.queryForObject("""
        select count(*) as total,
               count(*) filter (where c.logo_url is not null and c.logo_url <> '') as with_logo,
               count(*) filter (where not exists (select 1 from job j where j.company_id = c.id)) as without_jobs
        from company c
        """, new MapSqlParameterSource(), (rs, i) -> new AdminCompanyStatsResponse(
        rs.getLong("total"), rs.getLong("with_logo"), rs.getLong("without_jobs")));
  }

  public List<AdminCompanyRowResponse> find(String q, String sort, AdminPageQuery page) {
    MapSqlParameterSource params = new MapSqlParameterSource()
        .addValue("limit", page.size())
        .addValue("offset", page.offset());
    return jdbc.query(SELECT + where(q, params) + " order by " + orderBy(sort) + " limit :limit offset :offset",
        params, ROW);
  }

  public long count(String q) {
    MapSqlParameterSource params = new MapSqlParameterSource();
    Long total = jdbc.queryForObject("select count(*) from company c" + where(q, params), params, Long.class);
    return total == null ? 0 : total;
  }

  public AdminCompanyRowResponse findRow(UUID id) {
    List<AdminCompanyRowResponse> rows = jdbc.query(SELECT + " where c.id = :id",
        new MapSqlParameterSource("id", id), ROW);
    return rows.isEmpty() ? null : rows.get(0);
  }

  public List<CompanyName> allNames() {
    return jdbc.query("select id, name from company", new MapSqlParameterSource(),
        (rs, i) -> new CompanyName(AdminSql.uuid(rs, "id"), rs.getString("name")));
  }

  public Map<UUID, Long> jobCounts(Collection<UUID> companyIds) {
    Map<UUID, Long> counts = new HashMap<>();
    if (companyIds.isEmpty()) {
      return counts;
    }
    jdbc.query("select company_id, count(*) as n from job where company_id in (:ids) group by company_id",
        new MapSqlParameterSource("ids", companyIds),
        rs -> {
          counts.put(AdminSql.uuid(rs, "company_id"), rs.getLong("n"));
        });
    return counts;
  }

  public List<String> provinces(UUID companyId) {
    return jdbc.queryForList("""
        select p.name_local
        from job_location jl
        join job j on j.id = jl.job_id
        join province p on p.id = jl.province_id
        where j.company_id = :id
        group by p.name_local
        order by count(*) desc, p.name_local
        """, new MapSqlParameterSource("id", companyId), String.class);
  }

  public List<UUID> jobIds(UUID companyId) {
    return jdbc.queryForList("select id from job where company_id = :id",
        new MapSqlParameterSource("id", companyId), UUID.class);
  }

  private static String where(String q, MapSqlParameterSource params) {
    String pattern = AdminSql.containsPattern(q);
    if (pattern == null) {
      return "";
    }
    params.addValue("q", pattern);
    return " where c.name ilike :q";
  }

  private static String orderBy(String sort) {
    return switch (sort == null ? "jobs" : sort.toLowerCase()) {
      case "recent" -> "c.id desc";
      case "name" -> "lower(c.name) asc, c.id asc";
      default -> "job_count desc, lower(c.name) asc, c.id asc";
    };
  }
}
