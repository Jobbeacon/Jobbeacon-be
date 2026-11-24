package com.jobbeacon.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.jobbeacon.repository.TitleMatchRepository.Title;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@DataJpaTest
@Testcontainers
@Import(TitleMatchRepository.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class TitleMatchRepositoryIT {

  private static final Duration MONTH = Duration.ofDays(30);

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
  private TitleMatchRepository repository;
  @Autowired
  private JdbcTemplate jdbc;

  @Test
  void titlesThatOnlyDifferInCaseOrSpacingAreKeptOnce() {
    repository.request(List.of("Kế  Toán", " kế toán ", "Tester", "  "));
    repository.request(List.of("TESTER"));

    assertThat(repository.findWithoutEmbedding(5, 10)).extracting(Title::key)
        .containsExactlyInAnyOrder("kế toán", "tester");
  }

  @Test
  void onlyOpenJobsAddTheirTitleAndRequestedTitlesGoFirst() {
    job("Tester", "1 day");
    job("tester", null);
    job("Kế toán", "-1 day");
    repository.request(List.of("Sale"));

    assertThat(repository.addOpenJobTitles()).isOne();
    assertThat(repository.addOpenJobTitles()).isZero();
    assertThat(repository.findWithoutEmbedding(5, 10)).extracting(Title::key).containsExactly("sale", "tester");
  }

  @Test
  void embeddedAndRepeatedlyFailedTitlesAreNoLongerPending() {
    repository.request(List.of("done", "failed"));
    repository.saveEmbedding("done", vector(1, 0), "model");
    assertThat(repository.findWithoutEmbedding(2, 10)).extracting(Title::key).containsExactly("failed");
    repository.countFailure("failed");
    repository.countFailure("failed");

    assertThat(repository.findWithoutEmbedding(2, 10)).isEmpty();
  }

  @Test
  void aJobTitleAUserAlsoLooksForCountsAsRequested() {
    job("Tester", null);
    repository.addOpenJobTitles();
    repository.saveEmbedding("tester", vector(1, 0), "model");
    assertThat(repository.findRequested(MONTH, 10)).isEmpty();

    repository.request(List.of("Tester"));

    assertThat(repository.findRequested(MONTH, 10)).extracting(Title::key).containsExactly("tester");
  }

  @Test
  void requestedTitlesTakeTurns() {
    embed("a", 1, 0);
    embed("b", 1, 0);
    repository.markMatched("a");

    assertThat(repository.findRequested(MONTH, 10)).extracting(Title::key).containsExactly("b", "a");
  }

  @Test
  void onlyTheNearestOpenJobTitlesAreLeftToJudgeOnce() {
    job("Kỹ sư Backend", null);
    job("Backend Engineer", "1 day");
    job("Expired", "-1 day");
    job("Kế toán", null);
    embed("Backend Developer", 1, 0);
    embed("Kỹ sư Backend", 1, 0.5f);
    embed("Backend Engineer", 1, 0.1f);
    embed("Expired", 1, 0);
    embed("Kế toán", 0, 1);

    assertThat(repository.findUnjudged("backend developer", 80, 0.8, 80)).extracting(Title::key)
        .containsExactly("backend engineer", "kỹ sư backend");
    assertThat(repository.findUnjudged("backend developer", 1, 0.8, 80)).hasSize(1);

    repository.saveJudged("backend developer", List.of("backend engineer"), List.of("kỹ sư backend"));
    repository.saveJudged("backend developer", List.of("backend engineer"), List.of());

    assertThat(repository.findUnjudged("backend developer", 80, 0.8, 80)).isEmpty();
  }

  @Test
  void readsWhichOpenJobsMatch() {
    UUID match = job("Kỹ sư  Backend", null);
    UUID other = job("Kế toán", null);
    UUID expired = job("kỹ sư backend", "-1 day");
    repository.request(List.of("Backend Developer", "Kỹ sư Backend", "Kế toán"));
    repository.saveJudged("backend developer", List.of("kỹ sư backend"), List.of("kế toán"));
    List<String> titles = List.of("backend  developer", "unknown");

    assertThat(repository.findMatchingOpenJobs(titles, 10)).containsExactly(match);
    assertThat(repository.findMatchingTitles(titles, List.of(match, other, expired)))
        .isEqualTo(Map.of(match, Set.of("backend  developer"), expired, Set.of("backend  developer")));
  }

  private void embed(String title, float x, float y) {
    repository.request(List.of(title));
    repository.saveEmbedding(title.toLowerCase(), vector(x, y), "model");
  }

  private static float[] vector(float x, float y) {
    float[] vector = new float[1536];
    vector[0] = x;
    vector[1] = y;
    return vector;
  }

  private UUID job(String title, String expiresIn) {
    UUID id = UUID.randomUUID();
    jdbc.update("insert into job (id, title, expired_date) values (?, ?, now() + cast(? as interval))", id, title, expiresIn);
    return id;
  }
}
