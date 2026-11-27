package com.jobbeacon.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.jobbeacon.repository.SemanticEmbeddingRepository.Kind;
import com.jobbeacon.repository.SemanticEmbeddingRepository.Pending;
import com.jobbeacon.repository.SemanticEmbeddingRepository.PendingJob;
import com.jobbeacon.repository.SemanticEmbeddingRepository.State;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/** On a Postgres built by the full Liquibase changelog. Not in one transaction: now() must move between statements. */
@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(SemanticEmbeddingRepository.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class SemanticEmbeddingRepositoryIT {

  private static final String MODEL = "model-a";

  @Container
  private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
      DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

  @DynamicPropertySource
  static void datasource(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
  }

  @Autowired
  private SemanticEmbeddingRepository repository;
  @Autowired
  private JdbcTemplate jdbc;

  @BeforeEach
  void clean() {
    jdbc.execute("truncate job_embedding, candidate_embedding, job_skill_mapping, job_category_mapping, job, "
        + "cv_skill_mapping, cv_profile, user_job_preference, users cascade");
  }

  @Test
  void whatExistedBeforeIsAddedButNotExpiredJobsOrUsersWithNothingToCompare() {
    UUID open = job("Kế toán", null);
    job("Old", "now() - interval '1 day'");
    UUID withCv = user();
    jdbc.update("insert into cv_profile (id, user_id) values (?, ?)", UUID.randomUUID(), withCv);
    UUID withTitles = user();
    jdbc.update("insert into user_job_preference (id, user_id, desired_titles) values (?, ?, '{Tester}')",
        UUID.randomUUID(), withTitles);
    UUID withoutTitles = user();
    jdbc.update("insert into user_job_preference (id, user_id, desired_titles) values (?, ?, '{}')",
        UUID.randomUUID(), withoutTitles);
    user();

    repository.addMissing(MODEL);

    assertThat(jdbc.queryForList("select job_id from job_embedding where stale", UUID.class)).containsExactly(open);
    assertThat(jdbc.queryForList("select user_id from candidate_embedding where stale", UUID.class))
        .containsExactlyInAnyOrder(withCv, withTitles);
  }

  @Test
  void pendingJobsComeWithWhatTheirTextIsMadeOf() {
    UUID job = job("Backend Developer", null);
    UUID java = skill("Java");
    UUID spring = skill("Spring");
    jdbc.update("insert into job_skill_mapping (job_id, skill_id) values (?, ?), (?, ?)", job, spring, job, java);
    UUID category = UUID.randomUUID();
    jdbc.update("insert into category (id, name, path) values (?, 'Backend', 'IT/Backend')", category);
    jdbc.update("insert into job_category_mapping (job_id, category_id) values (?, ?)", job, category);
    repository.markJobChanged(job);

    PendingJob pending = repository.findPendingJobs(10).get(0);

    assertThat(pending.id()).isEqualTo(job);
    assertThat(pending.skills()).containsExactly("Java", "Spring");
    assertThat(pending.categories()).containsExactly("IT/Backend");
    assertThat(pending.state().textHash()).isNull();
  }

  @Test
  void aSavedVectorIsNoLongerPendingUnlessMarkedAgainMeanwhile() {
    UUID kept = user();
    UUID changed = user();
    repository.markCandidateChanged(kept);
    repository.markCandidateChanged(changed);
    List<Pending> read = repository.findPendingCandidates(10);

    repository.markCandidateChanged(changed);
    for (Pending pending : read) {
      repository.save(Kind.CANDIDATE, pending.id(), pending.state(), vector(1, 0), MODEL, "hash");
    }

    assertThat(repository.findPendingCandidates(10)).extracting(Pending::id).containsExactly(changed);
    assertThat(jdbc.queryForObject("select text_hash from candidate_embedding where user_id = ?", String.class, kept))
        .isEqualTo("hash");
  }

  @Test
  void aFailureWaitsLongerEachTimeInsteadOfGivingUp() {
    UUID job = job("x", null);
    repository.markJobChanged(job);

    repository.countFailure(Kind.JOB, job);
    repository.countFailure(Kind.JOB, job);

    assertThat(repository.findPendingJobs(10)).isEmpty();
    assertThat(jdbc.queryForObject(
        "select next_attempt_at > now() + interval '1 minute' and stale from job_embedding", Boolean.class)).isTrue();
  }

  @Test
  void vectorsOfAnotherModelBecomeStale() {
    UUID job = job("x", null);
    repository.markJobChanged(job);
    PendingJob pending = repository.findPendingJobs(10).get(0);
    repository.save(Kind.JOB, job, pending.state(), vector(1, 0), MODEL, "hash");

    repository.addMissing("model-b");

    assertThat(repository.findPendingJobs(10)).extracting(PendingJob::id).containsExactly(job);
  }

  @Test
  void nearestOpenJobsOfTheSameModelComeFirst() {
    UUID candidate = user();
    embedded(Kind.CANDIDATE, candidate, vector(1, 0), MODEL);
    UUID close = job("close", null);
    embedded(Kind.JOB, close, vector(1, 0.1f), MODEL);
    UUID far = job("far", null);
    embedded(Kind.JOB, far, vector(0, 1), MODEL);
    UUID expired = job("expired", "now() - interval '1 day'");
    embedded(Kind.JOB, expired, vector(1, 0), MODEL);
    UUID otherModel = job("other model", null);
    embedded(Kind.JOB, otherModel, vector(1, 0), "model-b");

    assertThat(repository.findNearestOpenJobs(candidate, 10)).containsExactly(close, far);
    assertThat(repository.findNearestOpenJobs(user(), 10)).isEmpty();
  }

  /** Expired jobs are never pending, so the state is read straight from the table. */
  private void embedded(Kind kind, UUID id, float[] vector, String model) {
    String table = kind == Kind.JOB ? "job_embedding" : "candidate_embedding";
    String key = kind == Kind.JOB ? "job_id" : "user_id";
    if (kind == Kind.JOB) {
      repository.markJobChanged(id);
    } else {
      repository.markCandidateChanged(id);
    }
    OffsetDateTime marked = jdbc.queryForObject(
        "select next_attempt_at from " + table + " where " + key + " = ?", OffsetDateTime.class, id);
    repository.save(kind, id, new State(null, null, marked), vector, model, "hash");
  }

  private UUID job(String title, String expiredDate) {
    UUID id = UUID.randomUUID();
    jdbc.update("insert into job (id, title, expired_date) values (?, ?, " + (expiredDate == null ? "null" : expiredDate)
        + ")", id, title);
    return id;
  }

  private UUID user() {
    UUID id = UUID.randomUUID();
    jdbc.update("insert into users (id, firebase_uid, email, role) values (?, ?, ?, 'USER')", id, id.toString(),
        id + "@example.com");
    return id;
  }

  private UUID skill(String name) {
    UUID id = UUID.randomUUID();
    jdbc.update("insert into skill (id, name) values (?, ?)", id, name);
    return id;
  }

  /** A 1536-dimension vector with the given leading values. */
  private static float[] vector(float... head) {
    float[] vector = new float[1536];
    System.arraycopy(head, 0, vector, 0, head.length);
    return vector;
  }
}
