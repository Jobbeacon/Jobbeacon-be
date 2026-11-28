package com.jobbeacon.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.Refresh;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.jobbeacon.repository.MarketElasticsearchRepository.Filter;
import com.jobbeacon.repository.MarketElasticsearchRepository.Group;
import com.jobbeacon.repository.MarketElasticsearchRepository.Overview;
import com.jobbeacon.search.ElasticsearchProperties;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

/** Market aggregations on a throwaway Elasticsearch whose fields are mapped as Jobbeacon-sync's index does. */
class MarketElasticsearchRepositoryIT {

  private static final String INDEX = "jobs";
  private static final double USD = 25_000;
  private static final Filter ALL = new Filter(null, null, null);

  private static final GenericContainer<?> ELASTICSEARCH =
      new GenericContainer<>("docker.elastic.co/elasticsearch/elasticsearch:8.15.3")
          .withEnv("discovery.type", "single-node")
          .withEnv("xpack.security.enabled", "false")
          .withEnv("ES_JAVA_OPTS", "-Xms512m -Xmx512m")
          .withExposedPorts(9200)
          .waitingFor(Wait.forHttp("/_cluster/health").forStatusCode(200).withStartupTimeout(Duration.ofMinutes(3)));

  private static RestClient restClient;
  private static ElasticsearchClient client;

  private final MarketElasticsearchRepository repository = new MarketElasticsearchRepository(client, properties());

  @BeforeAll
  static void start() {
    ELASTICSEARCH.start();
    restClient = RestClient.builder(new HttpHost(ELASTICSEARCH.getHost(), ELASTICSEARCH.getMappedPort(9200))).build();
    client = new ElasticsearchClient(new RestClientTransport(restClient, new JacksonJsonpMapper()));
  }

  @AfterAll
  static void stop() throws IOException {
    restClient.close();
    ELASTICSEARCH.stop();
  }

  @BeforeEach
  void createIndex() throws IOException {
    client.indices().delete(request -> request.index(INDEX).ignoreUnavailable(true));
    client.indices().create(request -> request
        .index(INDEX)
        .mappings(mappings -> mappings
            .properties("skills", property -> property.object(object -> object
                .properties("id", field -> field.keyword(keyword -> keyword))))
            .properties("categories", property -> property.object(object -> object
                .properties("id", field -> field.keyword(keyword -> keyword))))
            .properties("province_ids", property -> property.keyword(keyword -> keyword))
            .properties("experience_months", property -> property.integer(number -> number))
            .properties("min_salary", property -> property.long_(number -> number))
            .properties("max_salary", property -> property.long_(number -> number))
            .properties("currency", property -> property.keyword(keyword -> keyword))
            .properties("salary_period", property -> property.keyword(keyword -> keyword))
            .properties("posted_date", property -> property.date(date -> date))));
  }

  @Test
  void salariesAreMonthlyVndAtTheMiddleOfTheRange() throws IOException {
    index(job().province("vnd-range").salary(10_000_000L, 20_000_000L, "VND", "MONTH"));
    index(job().province("usd").salary(1_000L, 3_000L, "USD", "MONTH"));
    index(job().province("yearly").salary(240_000_000L, null, "VND", "YEAR"));
    index(job().province("only-max").salary(null, 30_000_000L, "VND", null));
    index(job().province("negotiable").salary(0L, 0L, "VND", "MONTH"));
    index(job().province("typo").salary(15_000L, 20_000L, "VND", "MONTH"));
    index(job().province("none"));

    Overview overview = repository.overview(ALL, Map.of(), USD);

    assertThat(overview.totalJobs()).isEqualTo(7);
    assertThat(overview.salary().sampleSize()).isEqualTo(4);
    assertThat(median(overview.provinces(), "vnd-range")).isEqualTo(15_000_000L);
    assertThat(median(overview.provinces(), "usd")).isEqualTo(50_000_000L);
    assertThat(median(overview.provinces(), "yearly")).isEqualTo(20_000_000L);
    assertThat(median(overview.provinces(), "only-max")).isEqualTo(30_000_000L);
    assertThat(group(overview.provinces(), "negotiable").salary().sampleSize()).isZero();
    assertThat(group(overview.provinces(), "typo").salary().sampleSize()).isZero();
    assertThat(group(overview.provinces(), "none").salary().median()).isNull();
  }

  @Test
  void filtersNarrowEveryFigure() throws IOException {
    String java = UUID.randomUUID().toString();
    String sql = UUID.randomUUID().toString();
    String it = UUID.randomUUID().toString();
    String backend = UUID.randomUUID().toString();
    index(job().skills(java, sql).categories(backend).province("01").salary(20_000_000L, 20_000_000L, "VND", "MONTH"));
    index(job().skills(java).categories(it).province("79"));
    index(job().skills(sql).province("01"));

    assertThat(repository.overview(new Filter(List.of(it, backend), null, null), Map.of(), USD).totalJobs()).isEqualTo(2);
    assertThat(repository.overview(new Filter(List.of(), null, null), Map.of(), USD).totalJobs()).isZero();
    assertThat(repository.overview(new Filter(null, "01", null), Map.of(), USD).totalJobs()).isEqualTo(2);
    Overview javaJobs = repository.overview(new Filter(null, null, java), Map.of(), USD);
    assertThat(javaJobs.totalJobs()).isEqualTo(2);
    assertThat(javaJobs.salary().sampleSize()).isOne();
    assertThat(javaJobs.skills()).extracting(Group::key, Group::jobCount)
        .containsExactly(tuple(java, 2L), tuple(sql, 1L));
  }

  @Test
  void categoryGroupsCoverTheirWholeSubtree() throws IOException {
    String root = UUID.randomUUID().toString();
    String child = UUID.randomUUID().toString();
    String other = UUID.randomUUID().toString();
    index(job().categories(root));
    index(job().categories(child));
    index(job().categories(other));

    Overview overview = repository.overview(ALL, Map.of("it", List.of(root, child), "sales", List.of(other)), USD);

    assertThat(group(overview.categories(), "it").jobCount()).isEqualTo(2);
    assertThat(group(overview.categories(), "sales").jobCount()).isEqualTo(1);
  }

  @Test
  void experienceAndWeeklyPostingsAreBucketed() throws IOException {
    Instant now = Instant.now();
    index(job().experience(0).posted(now));
    index(job().experience(6).posted(now));
    index(job().experience(24).posted(now));
    index(job().experience(36).posted(now.minus(Duration.ofDays(14))));
    index(job().experience(120).posted(now.minus(Duration.ofDays(400))));

    Overview overview = repository.overview(ALL, Map.of(), USD);

    assertThat(overview.experience()).extracting(Group::key, Group::jobCount).containsExactly(
        tuple("none", 1L),
        tuple("lt_1y", 1L),
        tuple("y1_3", 1L),
        tuple("y3_5", 1L),
        tuple("y5", 1L));
    // The job from 400 days ago is outside the trend window; the empty week between the other two is kept.
    assertThat(overview.trend()).extracting(MarketElasticsearchRepository.Week::jobCount).containsExactly(1L, 0L, 3L);
  }

  private static Long median(List<Group> groups, String key) {
    return group(groups, key).salary().median();
  }

  private static Group group(List<Group> groups, String key) {
    return groups.stream().filter(group -> group.key().equals(key)).findFirst().orElseThrow();
  }

  private static JobDocument job() {
    return new JobDocument();
  }

  private static void index(JobDocument job) throws IOException {
    client.index(request -> request.index(INDEX).id(UUID.randomUUID().toString()).document(job.fields)
        .refresh(Refresh.True));
  }

  private static ElasticsearchProperties properties() {
    ElasticsearchProperties properties = new ElasticsearchProperties();
    properties.setIndex(INDEX);
    return properties;
  }

  private static final class JobDocument {

    private final Map<String, Object> fields = new HashMap<>(Map.of("posted_date", Instant.now().toString()));

    JobDocument province(String id) {
      fields.put("province_ids", List.of(id));
      return this;
    }

    JobDocument skills(String... ids) {
      fields.put("skills", refs(ids));
      return this;
    }

    JobDocument categories(String... ids) {
      fields.put("categories", refs(ids));
      return this;
    }

    JobDocument experience(int months) {
      fields.put("experience_months", months);
      return this;
    }

    JobDocument posted(Instant date) {
      fields.put("posted_date", date.toString());
      return this;
    }

    JobDocument salary(Long min, Long max, String currency, String period) {
      if (min != null) {
        fields.put("min_salary", min);
      }
      if (max != null) {
        fields.put("max_salary", max);
      }
      fields.put("currency", currency);
      if (period != null) {
        fields.put("salary_period", period);
      }
      return this;
    }

    private static List<Map<String, String>> refs(String... ids) {
      return Arrays.stream(ids).map(id -> Map.of("id", id)).toList();
    }
  }
}
