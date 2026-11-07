package com.jobbeacon.graph;

import com.jobbeacon.repository.GraphSourceRepository;
import com.jobbeacon.repository.projection.GraphJob;
import com.jobbeacon.repository.projection.GraphJobSkill;
import com.jobbeacon.repository.projection.GraphJobSource;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.neo4j.driver.SessionConfig;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Pushes the current Postgres state of a set of jobs into Neo4j: the Job node, its Skill/Company nodes and the
 * REQUIRES_SKILL/POSTED_BY relationships. Ids that no longer exist in Postgres are removed from the graph.
 *
 * <p>Everything is derived from current state rather than from what changed, so syncing the same job twice is a
 * no-op. Job status is deliberately not stored: a job expires without any write, so queries filter on
 * {@code expires_at} instead.
 */
@Service
@ConditionalOnProperty(name = "app.graph.enabled", havingValue = "true")
public class GraphSyncService {

    private static final List<String> CONSTRAINTS = List.of(
        "CREATE CONSTRAINT candidate_id IF NOT EXISTS FOR (c:Candidate) REQUIRE c.id IS UNIQUE",
        "CREATE CONSTRAINT job_id IF NOT EXISTS FOR (j:Job) REQUIRE j.id IS UNIQUE",
        "CREATE CONSTRAINT skill_id IF NOT EXISTS FOR (s:Skill) REQUIRE s.id IS UNIQUE",
        "CREATE CONSTRAINT company_id IF NOT EXISTS FOR (c:Company) REQUIRE c.id IS UNIQUE"
    );

    private static final String UPSERT_SKILLS = """
        UNWIND $skills AS row
        MERGE (s:Skill {id: row.id})
        SET s.name = row.name, s.normalized_name = row.normalized_name
        """;

    private static final String UPSERT_COMPANIES = """
        UNWIND $companies AS row
        MERGE (c:Company {id: row.id})
        SET c.name = row.name, c.normalized_name = row.normalized_name
        """;

    private static final String UPSERT_JOBS = """
        UNWIND $jobs AS row
        MERGE (j:Job {id: row.id})
        SET j.title = row.title,
            j.sources = row.sources,
            j.source_urls = row.source_urls,
            j.published_at = row.published_at,
            j.expires_at = row.expires_at
        """;

    private static final String PRUNE_REQUIRES_SKILL = """
        UNWIND $jobs AS row
        MATCH (:Job {id: row.id})-[r:REQUIRES_SKILL]->(s:Skill)
        WHERE NOT s.id IN row.skill_ids
        DELETE r
        """;

    private static final String MERGE_REQUIRES_SKILL = """
        UNWIND $jobs AS row
        MATCH (j:Job {id: row.id})
        UNWIND row.skill_ids AS skillId
        MATCH (s:Skill {id: skillId})
        MERGE (j)-[:REQUIRES_SKILL]->(s)
        """;

    private static final String PRUNE_POSTED_BY = """
        UNWIND $jobs AS row
        MATCH (:Job {id: row.id})-[r:POSTED_BY]->(c:Company)
        WHERE row.company_id IS NULL OR c.id <> row.company_id
        DELETE r
        """;

    private static final String MERGE_POSTED_BY = """
        UNWIND $jobs AS row
        WITH row WHERE row.company_id IS NOT NULL
        MATCH (j:Job {id: row.id})
        MATCH (c:Company {id: row.company_id})
        MERGE (j)-[:POSTED_BY]->(c)
        """;

    private static final String DELETE_JOBS = """
        UNWIND $deletedIds AS id
        MATCH (j:Job {id: id})
        DETACH DELETE j
        """;

    private final Driver driver;
    private final GraphSourceRepository sourceRepository;
    private final SessionConfig sessionConfig;
    private volatile boolean schemaReady;

    public GraphSyncService(Driver driver, GraphSourceRepository sourceRepository, GraphProperties properties) {
        this.driver = driver;
        this.sourceRepository = sourceRepository;
        this.sessionConfig = SessionConfig.forDatabase(properties.database());
    }

    public void syncJobs(Collection<UUID> jobIds) {
        if (jobIds.isEmpty()) {
            return;
        }
        List<GraphJob> jobs = sourceRepository.findGraphJobs(jobIds);
        Map<UUID, List<GraphJobSkill>> skillsByJob = sourceRepository.findGraphJobSkills(jobIds).stream()
            .collect(Collectors.groupingBy(GraphJobSkill::jobId));
        Map<UUID, List<GraphJobSource>> sourcesByJob = sourceRepository.findGraphJobSources(jobIds).stream()
            .collect(Collectors.groupingBy(GraphJobSource::jobId));

        Map<String, Map<String, Object>> skills = new LinkedHashMap<>();
        Map<String, Map<String, Object>> companies = new LinkedHashMap<>();
        List<Map<String, Object>> jobRows = new ArrayList<>();
        for (GraphJob job : jobs) {
            List<String> skillIds = new ArrayList<>();
            for (GraphJobSkill skill : skillsByJob.getOrDefault(job.id(), List.of())) {
                String skillId = skill.skillId().toString();
                skillIds.add(skillId);
                skills.computeIfAbsent(skillId, id -> row(
                    "id", id, "name", skill.name(), "normalized_name", skill.normalizedName()));
            }
            String companyId = job.companyId() == null ? null : job.companyId().toString();
            if (companyId != null) {
                companies.computeIfAbsent(companyId, id -> row(
                    "id", id, "name", job.companyName(), "normalized_name", normalizeName(job.companyName())));
            }
            List<GraphJobSource> sources = sourcesByJob.getOrDefault(job.id(), List.of());
            jobRows.add(row(
                "id", job.id().toString(),
                "title", job.title(),
                "company_id", companyId,
                "skill_ids", skillIds,
                "sources", sources.stream().map(GraphJobSource::domain).filter(d -> d != null).distinct().toList(),
                "source_urls", sources.stream().map(GraphJobSource::jobUrl).filter(u -> u != null).distinct().toList(),
                "published_at", toDateTime(job.postedDate()),
                "expires_at", toDateTime(job.expiredDate())
            ));
        }

        Set<String> deletedIds = jobIds.stream().map(UUID::toString).collect(Collectors.toCollection(LinkedHashSet::new));
        jobs.forEach(job -> deletedIds.remove(job.id().toString()));

        Map<String, Object> params = Map.of(
            "skills", List.copyOf(skills.values()),
            "companies", List.copyOf(companies.values()),
            "jobs", jobRows,
            "deletedIds", List.copyOf(deletedIds)
        );

        ensureSchema();
        try (Session session = driver.session(sessionConfig)) {
            // One transaction per batch: a job is never left half-written (e.g. node without its skills).
            session.executeWriteWithoutResult(tx -> {
                for (String cypher : List.of(UPSERT_SKILLS, UPSERT_COMPANIES, UPSERT_JOBS, PRUNE_REQUIRES_SKILL,
                    MERGE_REQUIRES_SKILL, PRUNE_POSTED_BY, MERGE_POSTED_BY, DELETE_JOBS)) {
                    tx.run(cypher, params).consume();
                }
            });
        }
    }

    // Created lazily rather than at startup so the backend still boots while Neo4j is down.
    private void ensureSchema() {
        if (schemaReady) {
            return;
        }
        try (Session session = driver.session(sessionConfig)) {
            CONSTRAINTS.forEach(constraint -> session.run(constraint).consume());
        }
        schemaReady = true;
    }

    private static String normalizeName(String name) {
        return name == null ? null : name.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private static OffsetDateTime toDateTime(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }

    // Map.of rejects null values, and nullable fields (e.g. expires_at) are expected here.
    private static Map<String, Object> row(Object... keyValues) {
        Map<String, Object> row = new HashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            row.put((String) keyValues[i], keyValues[i + 1]);
        }
        return row;
    }
}
