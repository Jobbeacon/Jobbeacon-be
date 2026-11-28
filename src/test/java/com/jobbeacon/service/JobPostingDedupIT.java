package com.jobbeacon.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobbeacon.configuration.JobDedupProperties;
import com.jobbeacon.dto.message.JobPostingMessage;
import com.jobbeacon.graph.GraphSyncOutboxService;
import com.jobbeacon.repository.JobElasticsearchRepository;
import com.jobbeacon.repository.SemanticEmbeddingRepository;
import com.jobbeacon.search.JobSearchOutboxService;
import com.jobbeacon.service.impl.JobDeduplicationServiceImpl;
import com.jobbeacon.service.impl.JobServiceImpl;
import com.jobbeacon.utils.SourceJobKeys;
import java.io.IOException;
import java.io.InputStream;
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
import org.springframework.boot.context.properties.EnableConfigurationProperties;
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
@Import({JobServiceImpl.class, JobDeduplicationServiceImpl.class, JobSearchOutboxService.class, GraphSyncOutboxService.class,
    SemanticEmbeddingRepository.class})
@EnableConfigurationProperties(JobDedupProperties.class)
// Each message commits on its own, as it does behind the RabbitMQ listener.
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class JobPostingDedupIT {

  private static final String JV = "https://www.vietnamworks.com/accounting-lead-2126092-jv";
  private static final String JD = "https://www.vietnamworks.com/accounting-lead-2126092-jd";
  private static final LocalDateTime CRAWLED = LocalDateTime.of(2026, 11, 21, 15, 54, 24, 883_123_456);

  private static final String ITVIEC = "https://itviec.com/it-jobs/backend-developer-acme";
  private static final String TOPCV = "https://www.topcv.vn/viec-lam/backend-developer/123456.html";
  private static final String JOB_COLUMNS = "select title, description, min_salary, max_salary, expired_date, "
      + "experience_months, content_hash, minhash::text as minhash from job";
  private static final LocalDateTime POSTED = LocalDateTime.of(2026, 11, 10, 9, 0);
  private static final String BACKEND_DESCRIPTION = String.join(" ",
      "Acme is looking for a backend developer to build and run the services behind our payment platform.",
      "You will design REST APIs in Java and Spring Boot, model data in PostgreSQL, and publish events through RabbitMQ.",
      "You will review code, write automated tests, and take part in the on-call rotation with the rest of the team.",
      "We expect three years of experience with Java, good knowledge of SQL, and the habit of writing clear documentation.",
      "Experience with Docker, Kubernetes and cloud providers is a plus, as is any work on high traffic systems.");

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

  @Test
  void sameContentOnAnotherSitePostedCloseIsMergedWithBothKeys() {
    ingest(crossSite(ITVIEC, POSTED).build());
    Map<String, Object> before = jdbc.queryForMap(JOB_COLUMNS);

    ingest(crossSite(TOPCV, POSTED.plusDays(3)).title("Backend Developer (Java)").maxSalary(2600L)
        .minSalary(1000L).experienceMonths(36).expiredDate(POSTED.plusDays(40)).build());

    assertThat(count("job")).isOne();
    assertThat(jdbc.queryForMap(JOB_COLUMNS)).isEqualTo(before);
    assertThat(jdbc.queryForList("select source_job_key from job_source_mapping", String.class))
        .containsExactlyInAnyOrder(SourceJobKeys.from(ITVIEC), SourceJobKeys.from(TOPCV));
  }

  @Test
  void sameContentOnAnotherSitePostedFarApartIsMerged() {
    ingest(crossSite(ITVIEC, POSTED).build());
    ingest(crossSite(TOPCV, POSTED.plusDays(26)).build());

    assertThat(count("job")).isOne();
    assertThat(count("job_source_mapping")).isEqualTo(2);
  }

  @Test
  void sameContentOnAnotherSiteAfterTheOldJobExpiredStaysSeparate() {
    ingest(crossSite(ITVIEC, POSTED).expiredDate(POSTED.minusDays(1)).build());
    ingest(crossSite(TOPCV, POSTED.plusDays(3)).build());

    assertThat(count("job")).isEqualTo(2);
  }

  @Test
  void realCrossSitePairsAreMerged() throws IOException {
    for (String[] pair : new String[][] {{"c-vietnamworks", "d-itviec"}, {"e-vietnamworks-sap", "f-itviec-sap"}}) {
      setUp();
      ingest(fixture(pair[0]));
      ingest(fixture(pair[1]));

      assertThat(count("job")).as(pair[0] + " + " + pair[1]).isOne();
      assertThat(count("job_source_mapping")).as(pair[0] + " + " + pair[1]).isEqualTo(2);
    }
  }

  @Test
  void sameContentUnderAnotherKeyOnTheSameSiteStaysSeparate() {
    ingest(crossSite(ITVIEC, POSTED).build());
    ingest(crossSite(ITVIEC + "-2", POSTED).build());

    assertThat(count("job")).isEqualTo(2);
    assertThat(count("job_source_mapping")).isEqualTo(2);
  }

  @Test
  void laterCrawlOfAMergedCopyUpdatesTheJobWithoutANewMapping() {
    ingest(crossSite(ITVIEC, POSTED).build());
    ingest(crossSite(TOPCV, POSTED.plusDays(3)).build());

    ingest(crossSite(TOPCV, POSTED.plusDays(3)).crawledAt(CRAWLED.plusHours(1)).title("Senior Backend Developer")
        .maxSalary(2500L).build());

    assertThat(count("job")).isOne();
    assertThat(count("job_source_mapping")).isEqualTo(2);
    assertThat(jdbc.queryForMap("select title, max_salary from job"))
        .containsEntry("title", "Senior Backend Developer").containsEntry("max_salary", 2500L);
    assertThat(jdbc.queryForObject("select crawled_at from job_source_mapping where source_job_key = ?",
        java.sql.Timestamp.class, SourceJobKeys.from(TOPCV)).toInstant())
        .isEqualTo(CRAWLED.plusHours(1).atZone(java.time.ZoneId.of("Asia/Ho_Chi_Minh")).toInstant()
            .truncatedTo(java.time.temporal.ChronoUnit.MICROS));
  }

  @Test
  void resentMergedCopyIsSkipped() {
    ingest(crossSite(ITVIEC, POSTED).build());
    ingest(crossSite(TOPCV, POSTED.plusDays(3)).build());
    List<String> ops = searchOps();
    clearInvocations(skillResolutionService, categoryClassificationService);

    ingest(crossSite(TOPCV, POSTED.plusDays(3)).build());

    assertThat(count("job")).isOne();
    assertThat(count("job_source_mapping")).isEqualTo(2);
    assertThat(searchOps()).isEqualTo(ops);
    verifyNoInteractions(skillResolutionService, categoryClassificationService);
  }

  @Test
  void updateWithoutDescriptionKeepsTheFingerprint() {
    ingest(crossSite(ITVIEC, POSTED).build());
    Map<String, Object> before = jdbc.queryForMap("select content_hash, minhash::text as minhash from job");
    List<String> bands = bands();
    assertThat(before.get("content_hash")).isNotNull();
    assertThat(bands).isNotEmpty();

    ingest(crossSite(ITVIEC, POSTED).crawledAt(CRAWLED.plusDays(1)).description(" ").build());

    assertThat(jdbc.queryForMap("select content_hash, minhash::text as minhash from job")).isEqualTo(before);
    assertThat(bands()).isEqualTo(bands);
  }

  /** What the listener does with a message. */
  private void ingest(JobPostingMessage message) {
    if (!jobService.tryMergeDuplicate(message)) {
      jobService.processJobPostingMessage(message);
    }
  }

  /** A real crawled posting from {@code src/test/resources/dedup}. */
  private static JobPostingMessage fixture(String name) throws IOException {
    JsonNode node;
    try (InputStream in = JobPostingDedupIT.class.getResourceAsStream("/dedup/" + name + ".json")) {
      node = new ObjectMapper().readTree(in);
    }
    return JobPostingMessage.builder().url(node.get("url").asText()).title(node.get("title").asText())
        .description(node.get("description").asText()).companyName(node.get("companyName").asText())
        .location(node.get("location").asText()).experienceMonths(node.get("experienceMonths").asInt())
        .crawledAt(LocalDateTime.parse(node.get("crawledAt").asText()))
        .postedDate(LocalDateTime.parse(node.get("postedDate").asText())).build();
  }

  private List<String> bands() {
    return jdbc.queryForList("select band_idx || ':' || band_hash from job_lsh_band order by band_idx", String.class);
  }

  private static JobPostingMessage.JobPostingMessageBuilder crossSite(String url, LocalDateTime postedDate) {
    return JobPostingMessage.builder().url(url).crawledAt(CRAWLED).postedDate(postedDate).title("Backend Developer")
        .description(BACKEND_DESCRIPTION).companyName("Acme").location("Hà Nội").maxSalary(2000L);
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
