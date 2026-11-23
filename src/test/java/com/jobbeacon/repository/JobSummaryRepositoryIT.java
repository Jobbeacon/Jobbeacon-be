package com.jobbeacon.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.jobbeacon.dto.response.JobSummaryResponse;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
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

/** The query behind {@code POST /internal/v1/jobs/summaries}, which the recommendation service reads job cards from. */
@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class JobSummaryRepositoryIT {

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
  private JobRepository jobRepository;
  @Autowired
  private JdbcTemplate jdbc;

  @Test
  void returnsCardsOfRequestedJobsThatHaveNotExpired() {
    UUID companyId = UUID.randomUUID();
    jdbc.update("INSERT INTO company (id, name, logo_url) VALUES (?, 'Acme', 'https://logo')", companyId);
    UUID open = job("Open", companyId, Instant.now().plus(Duration.ofDays(1)));
    UUID noExpiry = job("No expiry", null, null);
    UUID expired = job("Expired", companyId, Instant.now().minus(Duration.ofDays(1)));
    UUID notRequested = job("Not requested", companyId, null);

    List<JobSummaryResponse> summaries =
        jobRepository.findActiveSummariesByIds(List.of(open, noExpiry, expired, UUID.randomUUID()));

    assertThat(summaries).extracting(JobSummaryResponse::id).containsExactlyInAnyOrder(open, noExpiry)
        .doesNotContain(expired, notRequested);
    JobSummaryResponse card = summaries.stream().filter(s -> s.id().equals(open)).findFirst().orElseThrow();
    assertThat(card.companyName()).isEqualTo("Acme");
    assertThat(card.companyLogoUrl()).isEqualTo("https://logo");
  }

  private UUID job(String title, UUID companyId, Instant expiredDate) {
    UUID id = UUID.randomUUID();
    jdbc.update("INSERT INTO job (id, title, company_id, expired_date) VALUES (?, ?, ?, ?)",
        id, title, companyId, expiredDate == null ? null : Timestamp.from(expiredDate));
    return id;
  }
}
