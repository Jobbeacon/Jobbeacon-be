package com.jobbeacon.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Vectors of what jobs are about and what candidates look for, compared to recommend jobs by meaning. Rows are marked
 * stale by whatever changes their source and filled in the background ({@code SemanticEmbeddingWorker}). Plain SQL for
 * the vectors.
 */
@Repository
@RequiredArgsConstructor
public class SemanticEmbeddingRepository {

    private static final String OPEN = "(j.expired_date is null or j.expired_date > now())";

    private final NamedParameterJdbcTemplate jdbc;

    public enum Kind {
        JOB("job_embedding", "job_id"),
        CANDIDATE("candidate_embedding", "user_id");

        private final String table;
        private final String key;

        Kind(String table, String key) {
            this.table = table;
            this.key = key;
        }
    }

    /** Call in the transaction that changes the job, so that the mark commits or rolls back with it. */
    public void markJobChanged(UUID jobId) {
        mark(Kind.JOB, jobId);
    }

    /** The same for the CV or the job preference of a user. */
    public void markCandidateChanged(UUID userId) {
        mark(Kind.CANDIDATE, userId);
    }

    private void mark(Kind kind, UUID id) {
        jdbc.update("""
            insert into %1$s (%2$s) values (:id)
            on conflict (%2$s) do update set stale = true, attempts = 0, next_attempt_at = now()
            """.formatted(kind.table, kind.key), Map.of("id", id));
    }

    /**
     * Adds the open jobs and the candidates that have no row yet, such as everything from before this table existed,
     * and marks vectors of another model stale: they cannot be compared with new ones.
     */
    @Transactional
    public void addMissing(String model) {
        jdbc.update("""
            insert into job_embedding (job_id)
            select j.id from job j
            where %s and not exists (select 1 from job_embedding e where e.job_id = j.id)
            on conflict do nothing
            """.formatted(OPEN), Map.of());
        jdbc.update("""
            insert into candidate_embedding (user_id)
            select u.id from users u
            where (exists (select 1 from cv_profile p where p.user_id = u.id)
                   or exists (select 1 from user_job_preference p
                              where p.user_id = u.id and cardinality(p.desired_titles) > 0))
              and not exists (select 1 from candidate_embedding e where e.user_id = u.id)
            on conflict do nothing
            """, Map.of());
        for (Kind kind : Kind.values()) {
            jdbc.update("""
                update %s set stale = true, attempts = 0, next_attempt_at = now()
                where not stale and model is distinct from :model
                """.formatted(kind.table), Map.of("model", model));
        }
    }

    /** Stale rows of open jobs that are due, newest job first, with what their text is made of. */
    public List<PendingJob> findPendingJobs(int limit) {
        return jdbc.query("""
            select j.id, j.title, j.description, e.text_hash, e.model, e.next_attempt_at,
                   array(select s.name from job_skill_mapping m join skill s on s.id = m.skill_id
                         where m.job_id = j.id and s.name is not null order by s.name) as skills,
                   array(select coalesce(c.path, c.name) from job_category_mapping m
                         join category c on c.id = m.category_id
                         where m.job_id = j.id and coalesce(c.path, c.name) is not null order by 1) as categories
            from job_embedding e
            join job j on j.id = e.job_id
            where e.stale and e.next_attempt_at <= now() and %s
            order by j.posted_date desc nulls last, j.id
            limit :limit
            """.formatted(OPEN), Map.of("limit", limit),
            (rs, row) -> new PendingJob(
                rs.getObject("id", UUID.class),
                rs.getString("title"),
                rs.getString("description"),
                strings(rs, "skills"),
                strings(rs, "categories"),
                state(rs)));
    }

    /** Stale candidate rows that are due, the one waiting longest first. */
    public List<Pending> findPendingCandidates(int limit) {
        return jdbc.query("""
            select user_id, text_hash, model, next_attempt_at from candidate_embedding
            where stale and next_attempt_at <= now()
            order by next_attempt_at, user_id
            limit :limit
            """, Map.of("limit", limit),
            (rs, row) -> new Pending(rs.getObject("user_id", UUID.class), state(rs)));
    }

    /** Skill names of the user's CV. */
    public List<String> findCandidateSkills(UUID userId) {
        return jdbc.queryForList("""
            select s.name from cv_profile p
            join cv_skill_mapping m on m.cv_profile_id = p.id
            join skill s on s.id = m.skill_id
            where p.user_id = :userId and s.name is not null
            order by s.name
            """, Map.of("userId", userId), String.class);
    }

    /** Stores the vector; the row stays stale when it was marked again since it was read. */
    @Transactional
    public void save(Kind kind, UUID id, State seen, float[] embedding, String model, String textHash) {
        jdbc.update("""
            update %s set embedding = cast(:embedding as vector), model = :model, text_hash = :hash,
                          stale = next_attempt_at is distinct from cast(:seen as timestamptz), attempts = 0, embedded_at = now()
            where %s = :id
            """.formatted(kind.table, kind.key),
            params(id, seen).addValue("embedding", Arrays.toString(embedding)).addValue("model", model)
                .addValue("hash", textHash));
    }

    /** The text is the one the vector was made from: nothing to embed. */
    @Transactional
    public void keep(Kind kind, UUID id, State seen) {
        jdbc.update("""
            update %s set stale = next_attempt_at is distinct from cast(:seen as timestamptz), attempts = 0 where %s = :id
            """.formatted(kind.table, kind.key), params(id, seen));
    }

    /** Nothing to compare: a job without text, or a candidate whose CV and preference are gone. */
    @Transactional
    public void clear(Kind kind, UUID id, State seen, String model) {
        jdbc.update("""
            update %s set embedding = null, text_hash = null, model = :model,
                          stale = next_attempt_at is distinct from cast(:seen as timestamptz), attempts = 0, embedded_at = now()
            where %s = :id
            """.formatted(kind.table, kind.key), params(id, seen).addValue("model", model));
    }

    /** Tried again after 1, 2, 4… minutes, at most a day: Gemini being down for long must not lose the row. */
    @Transactional
    public void countFailure(Kind kind, UUID id) {
        jdbc.update("""
            update %s set attempts = attempts + 1,
                          next_attempt_at = now() + least(interval '1 minute' * power(2, least(attempts, 20)),
                                                          interval '1 day')
            where %s = :id
            """.formatted(kind.table, kind.key), Map.of("id", id));
    }

    /** Open jobs closest in meaning to what the user looks for, closest first; empty while the user has no vector. */
    public List<UUID> findNearestOpenJobs(UUID userId, int limit) {
        return jdbc.query("""
            select e.job_id
            from candidate_embedding c
            join job_embedding e on e.model = c.model and e.embedding is not null
            join job j on j.id = e.job_id
            where c.user_id = :userId and c.embedding is not null and %s
            order by e.embedding <=> c.embedding, e.job_id
            limit :limit
            """.formatted(OPEN), Map.of("userId", userId, "limit", limit),
            (rs, row) -> rs.getObject("job_id", UUID.class));
    }

    private static MapSqlParameterSource params(UUID id, State seen) {
        return new MapSqlParameterSource("id", id).addValue("seen", seen.markedAt());
    }

    private static State state(ResultSet rs) throws SQLException {
        return new State(rs.getString("text_hash"), rs.getString("model"),
            rs.getObject("next_attempt_at", OffsetDateTime.class));
    }

    private static List<String> strings(ResultSet rs, String column) throws SQLException {
        Array array = rs.getArray(column);
        return array == null ? List.of() : List.of((String[]) array.getArray());
    }

    /** What the row held when it was read; {@code markedAt} tells whether it was marked again meanwhile. */
    public record State(String textHash, String model, OffsetDateTime markedAt) {
    }

    public record Pending(UUID id, State state) {
    }

    public record PendingJob(UUID id, String title, String description, List<String> skills, List<String> categories,
                             State state) {
    }
}
