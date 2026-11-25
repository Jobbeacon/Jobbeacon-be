package com.jobbeacon.repository.admin;

import com.jobbeacon.dto.response.admin.AdminOverviewResponse.RecentCrawl;
import com.jobbeacon.utils.AdminTime;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

/**
 * Crawl statistics over job_source_mapping: each row is one sighting of a job on one source.
 */
@Repository
@RequiredArgsConstructor
public class AdminOverviewQueryRepository {

  private static final String LOCAL_TIME = "(m.crawled_at at time zone '" + AdminTime.ZONE_ID + "')";

  private final NamedParameterJdbcTemplate jdbc;

  public record SourceCount(String key, String domain, long count) {
  }

  /** Every known source, the busiest first. */
  public List<String> sources() {
    return jdbc.queryForList("""
        select s.domain
        from job_source s
        left join job_source_mapping m on m.source_id = s.id
        where s.domain is not null
        group by s.domain
        order by count(m.job_id) desc, s.domain
        """, new MapSqlParameterSource(), String.class);
  }

  public List<SourceCount> countByDay(Instant from) {
    return countBy("to_char(" + LOCAL_TIME + ", 'YYYY-MM-DD')", from);
  }

  public List<SourceCount> countByMonth(Instant from) {
    return countBy("to_char(" + LOCAL_TIME + ", 'YYYY-MM')", from);
  }

  public List<SourceCount> countByHour(Instant from) {
    return countBy("cast(extract(hour from " + LOCAL_TIME + ") as int)::text", from);
  }

  private List<SourceCount> countBy(String keyExpression, Instant from) {
    String sql = "select " + keyExpression + " as k, s.domain as domain, count(*) as n "
        + "from job_source_mapping m join job_source s on s.id = m.source_id "
        + "where m.crawled_at >= :from group by 1, 2";
    return jdbc.query(sql, new MapSqlParameterSource("from", AdminSql.ts(from)),
        (rs, i) -> new SourceCount(rs.getString("k"), rs.getString("domain"), rs.getLong("n")));
  }

  public List<RecentCrawl> recent(int limit) {
    return jdbc.query("""
        select m.crawled_at, s.domain, m.job_url, j.id as job_id, j.title, c.id as company_id, c.name as company_name
        from job_source_mapping m
        join job_source s on s.id = m.source_id
        join job j on j.id = m.job_id
        left join company c on c.id = j.company_id
        where m.crawled_at is not null
        order by m.crawled_at desc
        limit :limit
        """, new MapSqlParameterSource("limit", limit), (rs, i) -> new RecentCrawl(
        AdminSql.instant(rs, "crawled_at"),
        rs.getString("domain"),
        rs.getString("job_url"),
        AdminSql.uuid(rs, "job_id"),
        rs.getString("title"),
        AdminSql.uuid(rs, "company_id"),
        rs.getString("company_name")
    ));
  }
}
