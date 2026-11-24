package com.jobbeacon.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.jobbeacon.entity.User;
import com.jobbeacon.repository.projection.GraphCandidateSavedJob;
import com.jobbeacon.repository.projection.GraphCandidateSkill;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/** The queries behind the internal graph API, on a throwaway Postgres built by the full Liquibase changelog. */
@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class GraphSourceRepositoryIT {

  private static final UUID NIL = new UUID(0, 0);

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
  private UserSavedJobRepository savedJobRepository;
  @Autowired
  private GraphSourceRepository graphSourceRepository;
  @Autowired
  private EntityManager entityManager;
  @Autowired
  private JdbcTemplate jdbc;

  private final UUID userId = UUID.randomUUID();
  private final UUID jobId = UUID.randomUUID();

  @BeforeEach
  void seed() {
    persistUser(userId);
    jdbc.update("INSERT INTO job (id, title) VALUES (?, 'Backend')", jobId);
  }

  @Test
  void savedJobWritesReportWhetherAnythingChanged() {
    assertThat(savedJobRepository.saveIfAbsent(userId, jobId)).isOne();
    assertThat(savedJobRepository.saveIfAbsent(userId, jobId)).isZero();
    assertThat(graphSourceRepository.findGraphCandidateSavedJobs(Set.of(userId)))
        .extracting(GraphCandidateSavedJob::jobId)
        .containsExactly(jobId);

    assertThat(savedJobRepository.deleteByUserIdAndJobId(userId, jobId)).isOne();
    assertThat(savedJobRepository.deleteByUserIdAndJobId(userId, jobId)).isZero();
  }

  @Test
  void candidateSkillsAreReadThroughTheCvProfile() {
    UUID profileId = UUID.randomUUID();
    UUID skillId = UUID.randomUUID();
    jdbc.update("INSERT INTO cv_profile (id, user_id) VALUES (?, ?)", profileId, userId);
    jdbc.update("INSERT INTO skill (id, name) VALUES (?, 'Java')", skillId);
    jdbc.update(
        "INSERT INTO cv_skill_mapping (cv_profile_id, skill_id, confidence, experience_months) VALUES (?, ?, 0.9, 24)",
        profileId, skillId);

    List<GraphCandidateSkill> skills = graphSourceRepository.findGraphCandidateSkills(Set.of(userId));

    assertThat(skills).hasSize(1);
    assertThat(skills.get(0).userId()).isEqualTo(userId);
    assertThat(skills.get(0).skillId()).isEqualTo(skillId);
    assertThat(skills.get(0).experienceMonths()).isEqualTo(24);
  }

  @Test
  void existingUsersAreReportedSoDeletedOnesCanBeRemovedFromTheGraph() {
    UUID deleted = UUID.randomUUID();

    assertThat(graphSourceRepository.findExistingUserIds(Set.of(userId, deleted))).containsExactly(userId);
  }

  @Test
  void jobIdsArePagedFromTheStartWithoutGapsOrRepeats() {
    jdbc.update("INSERT INTO job (id, title) VALUES (?, 'Second')", UUID.randomUUID());
    jdbc.update("INSERT INTO job (id, title) VALUES (?, 'Third')", UUID.randomUUID());
    List<UUID> all = graphSourceRepository.findJobIdsAfter(NIL, 100);

    List<UUID> first = graphSourceRepository.findJobIdsAfter(NIL, 2);
    List<UUID> rest = graphSourceRepository.findJobIdsAfter(first.get(1), 2);

    assertThat(all).hasSize(3);
    assertThat(first).hasSize(2);
    assertThat(rest).hasSize(1);
    assertThat(concat(first, rest)).containsExactlyElementsOf(all);
  }

  @Test
  void candidateIdsCoverCvAndSavedJobUsersOnce() {
    UUID cvOnly = UUID.randomUUID();
    UUID nothing = UUID.randomUUID();
    persistUser(cvOnly);
    persistUser(nothing);
    jdbc.update("INSERT INTO cv_profile (id, user_id) VALUES (?, ?)", UUID.randomUUID(), cvOnly);
    jdbc.update("INSERT INTO cv_profile (id, user_id) VALUES (?, ?)", UUID.randomUUID(), userId);
    savedJobRepository.saveIfAbsent(userId, jobId);

    assertThat(graphSourceRepository.findCandidateIdsAfter(NIL, 100)).containsExactlyInAnyOrder(userId, cvOnly);
  }

  private void persistUser(UUID id) {
    entityManager.persist(User.builder()
        .id(id)
        .firebaseUid(id.toString())
        .email(id + "@test.local")
        .build());
    entityManager.flush();
  }

  private static List<UUID> concat(List<UUID> first, List<UUID> second) {
    return java.util.stream.Stream.concat(first.stream(), second.stream()).toList();
  }
}
