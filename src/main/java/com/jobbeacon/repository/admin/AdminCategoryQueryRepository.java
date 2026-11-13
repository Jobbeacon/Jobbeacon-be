package com.jobbeacon.repository.admin;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class AdminCategoryQueryRepository {

  private final NamedParameterJdbcTemplate jdbc;

  /**
   * Distinct jobs per category counting the category itself and every descendant
   * (jobs are usually tagged with the deepest node only).
   */
  public Map<UUID, Long> jobCountsWithDescendants() {
    Map<UUID, Long> counts = new HashMap<>();
    jdbc.query("""
        with recursive anc as (
            select c.id as category_id, c.id as ancestor_id, c.parent_id, 1 as depth
            from category c
            union all
            select a.category_id, p.id, p.parent_id, a.depth + 1
            from anc a
            join category p on p.id = a.parent_id
            where a.depth < 10
        )
        select a.ancestor_id, count(distinct m.job_id) as n
        from anc a
        join job_category_mapping m on m.category_id = a.category_id
        group by a.ancestor_id
        """, new MapSqlParameterSource(), rs -> {
      counts.put(AdminSql.uuid(rs, "ancestor_id"), rs.getLong("n"));
    });
    return counts;
  }

  public List<UUID> jobIds(UUID categoryId) {
    return jdbc.queryForList("select job_id from job_category_mapping where category_id = :id",
        new MapSqlParameterSource("id", categoryId), UUID.class);
  }

  public int deleteJobMappings(UUID categoryId) {
    return jdbc.update("delete from job_category_mapping where category_id = :id",
        new MapSqlParameterSource("id", categoryId));
  }
}
