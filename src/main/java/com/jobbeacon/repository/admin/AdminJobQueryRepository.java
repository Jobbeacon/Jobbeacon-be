package com.jobbeacon.repository.admin;

import com.jobbeacon.dto.request.admin.AdminPageQuery;
import com.jobbeacon.dto.response.admin.AdminJobRowResponse;
import com.jobbeacon.dto.response.admin.AdminJobStatsResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class AdminJobQueryRepository {

  private static final String OPEN = "(j.expired_date is null or j.expired_date > now())";

  private static final String FROM = """
      from job j
      left join company c on c.id = j.company_id
      left join (
          select m.job_id, max(m.crawled_at) as last_crawled_at, array_agg(distinct s.domain) as sources
          from job_source_mapping m
          join job_source s on s.id = m.source_id
          group by m.job_id
      ) l on l.job_id = j.id
      """;

  private static final RowMapper<AdminJobRowResponse> ROW = (rs, i) -> new AdminJobRowResponse(
      AdminSql.uuid(rs, "id"),
      rs.getString("title"),
      AdminSql.uuid(rs, "company_id"),
      rs.getString("company_name"),
      AdminSql.strings(rs, "location"),
      AdminSql.longOrNull(rs, "min_salary"),
      AdminSql.longOrNull(rs, "max_salary"),
      rs.getString("currency"),
      rs.getString("salary_period"),
      rs.getString("employment_type"),
      AdminSql.instant(rs, "posted_date"),
      AdminSql.instant(rs, "expired_date"),
      AdminSql.sortedStrings(rs, "sources"),
      AdminSql.instant(rs, "last_crawled_at")
  );

  private final NamedParameterJdbcTemplate jdbc;

  public record Filter(
      String q,
      String source,
      String employmentType,
      UUID companyId,
      Instant crawledSince,
      String status,
      String sort
  ) {
  }

  public AdminJobStatsResponse stats(Instant startOfToday) {
    return jdbc.queryForObject("""
        select count(*) as total,
               count(*) filter (where %s) as open,
               count(*) filter (where not %s) as expired,
               (select count(distinct m.job_id) from job_source_mapping m where m.crawled_at >= :today) as crawled_today
        from job j
        """.formatted(OPEN, OPEN), new MapSqlParameterSource("today", AdminSql.ts(startOfToday)),
        (rs, i) -> new AdminJobStatsResponse(
            rs.getLong("total"), rs.getLong("open"), rs.getLong("expired"), rs.getLong("crawled_today")));
  }

  public List<AdminJobRowResponse> find(Filter filter, AdminPageQuery page) {
    MapSqlParameterSource params = new MapSqlParameterSource();
    String sql = "select j.id, j.title, c.id as company_id, c.name as company_name, j.location, j.min_salary, "
        + "j.max_salary, j.currency, j.salary_period, j.employment_type, j.posted_date, j.expired_date, "
        + "l.sources, l.last_crawled_at "
        + FROM + where(filter, params) + " order by " + orderBy(filter.sort())
        + " limit :limit offset :offset";
    params.addValue("limit", page.size()).addValue("offset", page.offset());
    return jdbc.query(sql, params, ROW);
  }

  public long count(Filter filter) {
    MapSqlParameterSource params = new MapSqlParameterSource();
    Long total = jdbc.queryForObject("select count(*) " + FROM + where(filter, params), params, Long.class);
    return total == null ? 0 : total;
  }

  public AdminJobRowResponse findRow(UUID id) {
    List<AdminJobRowResponse> rows = jdbc.query(
        "select j.id, j.title, c.id as company_id, c.name as company_name, j.location, j.min_salary, "
            + "j.max_salary, j.currency, j.salary_period, j.employment_type, j.posted_date, j.expired_date, "
            + "l.sources, l.last_crawled_at " + FROM + " where j.id = :id",
        new MapSqlParameterSource("id", id), ROW);
    return rows.isEmpty() ? null : rows.get(0);
  }

  private static String where(Filter filter, MapSqlParameterSource params) {
    List<String> clauses = new ArrayList<>();
    String pattern = AdminSql.containsPattern(filter.q());
    if (pattern != null) {
      clauses.add("(j.title ilike :q or c.name ilike :q or array_to_string(j.location, ' ') ilike :q)");
      params.addValue("q", pattern);
    }
    if (filter.source() != null && !filter.source().isBlank()) {
      clauses.add("""
          exists (select 1 from job_source_mapping m2 join job_source s2 on s2.id = m2.source_id
                  where m2.job_id = j.id and lower(s2.domain) = :source)""");
      params.addValue("source", AdminSql.lower(filter.source()));
    }
    if (filter.employmentType() != null && !filter.employmentType().isBlank()) {
      clauses.add("j.employment_type = :employmentType");
      params.addValue("employmentType", filter.employmentType().trim());
    }
    if (filter.companyId() != null) {
      clauses.add("j.company_id = :companyId");
      params.addValue("companyId", filter.companyId());
    }
    if (filter.crawledSince() != null) {
      clauses.add("exists (select 1 from job_source_mapping m3 where m3.job_id = j.id and m3.crawled_at >= :crawledSince)");
      params.addValue("crawledSince", AdminSql.ts(filter.crawledSince()));
    }
    if ("open".equalsIgnoreCase(filter.status())) {
      clauses.add(OPEN);
    } else if ("expired".equalsIgnoreCase(filter.status())) {
      clauses.add("not " + OPEN);
    }
    return clauses.isEmpty() ? "" : " where " + String.join(" and ", clauses);
  }

  private static String orderBy(String sort) {
    if (sort == null) {
      sort = "new";
    }
    return switch (sort.toLowerCase()) {
      case "old" -> "l.last_crawled_at asc nulls last, j.id asc";
      case "expiring" -> "case when " + OPEN + " and j.expired_date is not null then 0 else 1 end, "
          + "j.expired_date asc nulls last, j.id desc";
      case "salary" -> "coalesce(j.max_salary, j.min_salary) desc nulls last, j.id desc";
      default -> "l.last_crawled_at desc nulls last, j.id desc";
    };
  }
}
