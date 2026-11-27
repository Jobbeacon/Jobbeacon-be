package com.jobbeacon.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.jobbeacon.dto.message.JobPostingMessage;
import com.jobbeacon.graph.GraphSyncOutboxService;
import com.jobbeacon.repository.JobElasticsearchRepository;
import com.jobbeacon.repository.SemanticEmbeddingRepository;
import com.jobbeacon.search.JobSearchOutboxService;
import com.jobbeacon.service.impl.JobServiceImpl;
import com.jobbeacon.utils.SourceJobKeys;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/** Receiving one posting several times must leave one job. Runs on a Postgres built by the full Liquibase changelog. */
@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration(JacksonAutoConfiguration.class)
@Import({JobServiceImpl.class, JobSearchOutboxService.class, GraphSyncOutboxService.class,
    SemanticEmbeddingRepository.class})
// Each message commits on its own, as it does behind the RabbitMQ listener.
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class JobPostingDedupIT {

  private static final String JV = "https://www.vietnamworks.com/accounting-lead-2126092-jv";
  private static final String JD = "https://www.vietnamworks.com/accounting-lead-2126092-jd";
  private static final LocalDateTime CRAWLED = LocalDateTime.of(2026, 11, 21, 15, 54, 24, 883_123_456);

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
  private JobService jobService;
  @Autowired
  private JdbcTemplate jdbc;
  @Autowired
  private DataSource dataSource;
  @MockBean
  private SkillResolutionService skillResolutionService;
  @MockBean
  private CategoryClassificationService categoryClassificationService;
  @MockBean
  private LocationMappingService locationMappingService;
  @MockBean
  private JobElasticsearchRepository jobElasticsearchRepository;

  @BeforeEach
  void setUp() {
    jdbc.execute("truncate job_location, job_source_mapping, job_search_outbox, graph_sync_outbox, job, job_source, company cascade");
    when(skillResolutionService.resolveSkills(any())).thenReturn(new HashSet<>());
    when(categoryClassificationService.classifyDeepestCategory(any())).thenReturn(Optional.empty());
    when(locationMappingService.map(any())).thenAnswer(call ->
        new LocationMappingService.MappedLocation(call.getArgument(0), null, null));
  }

  @Test
  void newPostingIsStoredWithItsKey() {
    jobService.processJobPostingMessage(posting(JV, CRAWLED).build());

    assertThat(count("job")).isOne();
    assertThat(jdbc.queryForObject("select source_job_key from job_source_mapping", String.class)).isEqualTo("2126092");
    assertThat(searchOps()).containsExactly("c");
    // Marked in the same transaction, before JPA wrote the job: the foreign key is only checked at commit.
    assertThat(jdbc.queryForObject("select stale from job_embedding", Boolean.class)).isTrue();
  }

  @Test
  void redeliveredMessageIsSkippedBeforeAnyPaidCall() {
    jobService.processJobPostingMessage(posting(JV, CRAWLED).build());
    clearInvocations(skillResolutionService, categoryClassificationService);

    jobService.processJobPostingMessage(posting(JV, CRAWLED).build());

    assertThat(count("job")).isOne();
    assertThat(searchOps()).containsExactly("c");
    verifyNoInteractions(skillResolutionService, categoryClassificationService);
  }

  @Test
  void olderMessageDoesNotOverwriteANewerOne() {
    jobService.processJobPostingMessage(posting(JV, CRAWLED).title("New").build());

    jobService.processJobPostingMessage(posting(JD, CRAWLED.minusMinutes(5)).title("Old").build());

    assertThat(jdbc.queryForObject("select title from job", String.class)).isEqualTo("New");
  }

  @Test
  void laterCrawlUnderTheOtherUrlUpdatesTheSameJob() {
    jobService.processJobPostingMessage(posting(JV, CRAWLED).build());
    UUID id = jdbc.queryForObject("select id from job", UUID.class);

    jobService.processJobPostingMessage(
        posting(JD, CRAWLED.plusMinutes(6)).title("Renamed").location("Huế|Đà Nẵng").maxSalary(3000L).build());

    Map<String, Object> job = jdbc.queryForMap("select id, title, max_salary from job");
    assertThat(job).containsEntry("id", id).containsEntry("title", "Renamed").containsEntry("max_salary", 3000L);
    assertThat(jdbc.queryForObject("select job_url from job_source_mapping", String.class)).isEqualTo(JD);
    assertThat(jdbc.queryForList("select raw_location from job_location", String.class))
        .containsExactlyInAnyOrder("Huế", "Đà Nẵng");
    assertThat(searchOps()).containsExactly("c", "u");
    assertThat(count("graph_sync_outbox")).isEqualTo(2);
    assertThat(jdbc.queryForObject("select payload::text from job_search_outbox where op = 'u'", String.class))
        .contains("Renamed").contains("vietnamworks.com");
  }

  @Test
  void updateKeepsWhatTheCrawlerDidNotFindThisTime() {
    jobService.processJobPostingMessage(posting(JV, CRAWLED).build());

    jobService.processJobPostingMessage(JobPostingMessage.builder()
        .url(JV).title("Renamed").description(" ").crawledAt(CRAWLED.plusDays(1)).build());

    Map<String, Object> job = jdbc.queryForMap(
        "select title, description, max_salary, experience_months, location::text, company_id is not null as has_company from job");
    assertThat(job).containsEntry("title", "Renamed").containsEntry("description", "Text")
        .containsEntry("max_salary", 2000L).containsEntry("experience_months", 12)
        .containsEntry("location", "{\"Hà Nội\"}").containsEntry("has_company", true);
    assertThat(jdbc.queryForList("select raw_location from job_location", String.class)).containsExactly("Hà Nội");
  }

  @Test
  void differentPostingsWithTheSameTextStaySeparate() {
    jobService.processJobPostingMessage(posting(JV, CRAWLED).build());
    jobService.processJobPostingMessage(
        posting("https://www.vietnamworks.com/accounting-lead-2126093-jv", CRAWLED).build());
    jobService.processJobPostingMessage(posting("https://itviec.com/it-jobs/accounting-lead", CRAWLED).build());

    assertThat(count("job")).isEqualTo(3);
  }

  @Test
  void migrationKeysTheFirstCopyOfEachPostingAndKeepsTheRest() {
    UUID source = UUID.randomUUID();
    jdbc.update("insert into job_source (id, domain) values (?, 'vietnamworks.com')", source);
    UUID first = stored(source, JV, "2026-11-21T15:54:24Z");
    UUID copy = stored(source, JD, "2026-11-21T16:02:55Z");
    UUID again = stored(source, JV, "2026-11-21T16:00:26Z");
    String other = "https://www.vietnamworks.com/other/?x=1";
    UUID single = stored(source, other, "2026-11-21T16:00:00Z");
    String bare = "https://www.vietnamworks.com/";
    UUID noPath = stored(source, bare, "2026-11-21T16:00:00Z");

    migrate();
    migrate();

    assertThat(key(first)).isEqualTo(SourceJobKeys.from(JV));
    assertThat(key(copy)).isNull();
    assertThat(key(again)).isNull();
    assertThat(key(single)).isEqualTo(SourceJobKeys.from(other));
    assertThat(key(noPath)).isNull();
    assertThat(SourceJobKeys.from(bare)).isNull();
    assertThat(count("job")).isEqualTo(5);
    assertThatThrownBy(() -> jdbc.update(
        "update job_source_mapping set source_job_key = '2126092' where job_id = ?", copy))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  private void migrate() {
    new ResourceDatabasePopulator(new ClassPathResource("db/changelog/schema/008_job_source_key.sql")).execute(dataSource);
  }

  private UUID stored(UUID source, String url, String crawledAt) {
    UUID id = UUID.randomUUID();
    jdbc.update("insert into job (id, title) values (?, 'Accounting Lead')", id);
    jdbc.update("insert into job_source_mapping (source_id, job_id, job_url, crawled_at) values (?, ?, ?, ?::timestamptz)",
        source, id, url, crawledAt);
    return id;
  }

  private String key(UUID jobId) {
    return jdbc.queryForObject("select source_job_key from job_source_mapping where job_id = ?", String.class, jobId);
  }

  private int count(String table) {
    return jdbc.queryForObject("select count(*) from " + table, Integer.class);
  }

  private List<String> searchOps() {
    return jdbc.queryForList("select op from job_search_outbox order by created_at, id", String.class);
  }

  private static JobPostingMessage.JobPostingMessageBuilder posting(String url, LocalDateTime crawledAt) {
    return JobPostingMessage.builder().url(url).crawledAt(crawledAt).title("Accounting Lead").description("Text")
        .companyName("Acme").location("Hà Nội").maxSalary(2000L).experienceMonths(12);
  }
}
