package com.jobbeacon.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Titles, their embeddings, and which job titles name the occupation a user looks for. Plain SQL for the vectors. */
@Repository
@RequiredArgsConstructor
public class TitleMatchRepository {

    private static final String OPEN = "(j.expired_date is null or j.expired_date > now())";

    private final NamedParameterJdbcTemplate jdbc;

    /** Notes that a user looks for these titles, so that they get matched with job titles. */
    @Transactional
    public void request(Collection<String> titles) {
        if (titles.isEmpty()) {
            return;
        }
        jdbc.update("""
            insert into title_embedding (title_key, title, requested_at)
            select distinct on (title_key(t.title)) title_key(t.title), left(t.title, 255), now()
            from unnest(:titles) as t(title)
            where title_key(t.title) is not null
            order by title_key(t.title)
            on conflict (title_key) do update set requested_at = now()
            where title_embedding.requested_at is null or title_embedding.requested_at < now() - interval '1 day'
            """, titles(titles));
    }

    /** Queues the titles of open jobs that are not known yet. */
    @Transactional
    public int addOpenJobTitles() {
        return jdbc.update("""
            insert into title_embedding (title_key, title)
            select distinct on (j.title_key) j.title_key, j.title
            from job j
            where j.title_key is not null
              and %s
              and not exists (select 1 from title_embedding e where e.title_key = j.title_key)
            order by j.title_key
            on conflict do nothing
            """.formatted(OPEN), Map.of());
    }

    /** Titles without an embedding, those users look for first. */
    public List<Title> findWithoutEmbedding(int maxAttempts, int limit) {
        return jdbc.query("""
            select title_key, title from title_embedding
            where embedding is null and attempts < :maxAttempts
            order by attempts, requested_at desc nulls last, created_at desc
            limit :limit
            """, Map.of("maxAttempts", maxAttempts, "limit", limit),
            (rs, row) -> new Title(rs.getString("title_key"), rs.getString("title")));
    }

    @Transactional
    public void saveEmbedding(String titleKey, float[] embedding, String model) {
        jdbc.update("update title_embedding set embedding = cast(:embedding as vector), model = :model where title_key = :key",
            Map.of("key", titleKey, "embedding", Arrays.toString(embedding), "model", model));
    }

    @Transactional
    public void countFailure(String titleKey) {
        jdbc.update("update title_embedding set attempts = attempts + 1 where title_key = :key", Map.of("key", titleKey));
    }

    /** Titles users looked for lately, the one that was compared longest ago first. */
    public List<Title> findRequested(Duration within, int limit) {
        return jdbc.query("""
            select title_key, title from title_embedding
            where embedding is not null and requested_at > now() - make_interval(secs => :seconds)
            order by matched_at nulls first
            limit :limit
            """, Map.of("seconds", (double) within.toSeconds(), "limit", limit),
            (rs, row) -> new Title(rs.getString("title_key"), rs.getString("title")));
    }

    /** Open job titles among the {@code nearest} to the user's title that nobody has judged yet, nearest first. */
    public List<Title> findUnjudged(String userTitleKey, int nearest, double minSimilarity, int limit) {
        return jdbc.query("""
            with near as (
                select e.title_key, e.title, e.embedding <=> u.embedding as distance
                from title_embedding u
                join title_embedding e on e.embedding is not null and e.title_key <> u.title_key
                where u.title_key = :key
                  and exists (select 1 from job j where j.title_key = e.title_key and %s)
                order by distance
                limit :nearest
            )
            select title_key, title from near
            where 1 - distance >= :minSimilarity
              and not exists (
                  select 1 from title_match m where m.user_title_key = :key and m.job_title_key = near.title_key)
            order by distance
            limit :limit
            """.formatted(OPEN),
            Map.of("key", userTitleKey, "nearest", nearest, "minSimilarity", minSimilarity, "limit", limit),
            (rs, row) -> new Title(rs.getString("title_key"), rs.getString("title")));
    }

    @Transactional
    public void saveJudged(String userTitleKey, Collection<String> matching, Collection<String> others) {
        for (boolean matches : new boolean[] {true, false}) {
            jdbc.update("""
                insert into title_match (user_title_key, job_title_key, matches)
                select :key, k, :matches from unnest(:jobKeys) as k
                on conflict do nothing
                """, new MapSqlParameterSource("key", userTitleKey).addValue("matches", matches)
                    .addValue("jobKeys", (matches ? matching : others).toArray(String[]::new)));
        }
    }

    /** Also called when comparing failed, so that one bad title does not keep the others waiting. */
    @Transactional
    public void markMatched(String userTitleKey) {
        jdbc.update("update title_embedding set matched_at = now() where title_key = :key", Map.of("key", userTitleKey));
    }

    /** Open jobs whose title names the occupation of one of the titles, newest first. */
    public List<UUID> findMatchingOpenJobs(Collection<String> titles, int limit) {
        if (titles.isEmpty()) {
            return List.of();
        }
        return jdbc.query("""
            select j.id from job j
            where %s
              and exists (
                  select 1 from title_match m
                  join unnest(:titles) as t(title) on m.user_title_key = title_key(t.title)
                  where m.job_title_key = j.title_key and m.matches)
            order by j.posted_date desc nulls last, j.id
            limit :limit
            """.formatted(OPEN), titles(titles).addValue("limit", limit),
            (rs, row) -> rs.getObject("id", UUID.class));
    }

    /** For each of the jobs, which of the titles its own title names the occupation of. */
    public Map<UUID, Set<String>> findMatchingTitles(Collection<String> titles, Collection<UUID> jobIds) {
        Map<UUID, Set<String>> matching = new HashMap<>();
        if (titles.isEmpty() || jobIds.isEmpty()) {
            return matching;
        }
        jdbc.query("""
            select j.id, t.title
            from job j
            join title_match m on m.job_title_key = j.title_key and m.matches
            join unnest(:titles) as t(title) on m.user_title_key = title_key(t.title)
            where j.id = any(:jobIds)
            """, titles(titles).addValue("jobIds", jobIds.toArray(UUID[]::new)),
            rs -> {
                matching.computeIfAbsent(rs.getObject("id", UUID.class), id -> new HashSet<>()).add(rs.getString("title"));
            });
        return matching;
    }

    private static MapSqlParameterSource titles(Collection<String> titles) {
        return new MapSqlParameterSource("titles", titles.toArray(String[]::new));
    }

    public record Title(String key, String text) {
    }
}
