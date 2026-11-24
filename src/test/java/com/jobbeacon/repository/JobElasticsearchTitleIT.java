package com.jobbeacon.repository;

import static org.assertj.core.api.Assertions.assertThat;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.Refresh;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.jobbeacon.repository.JobElasticsearchRepository.TitleHit;
import com.jobbeacon.repository.JobElasticsearchRepository.TitleQuery;
import com.jobbeacon.search.ElasticsearchProperties;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
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

/** The title search on a throwaway Elasticsearch whose title field is analyzed as Jobbeacon-sync's index does. */
class JobElasticsearchTitleIT {

  private static final String INDEX = "jobs";
  private static final Instant NOW = Instant.now();

  private static final GenericContainer<?> ELASTICSEARCH =
      new GenericContainer<>("docker.elastic.co/elasticsearch/elasticsearch:8.15.3")
          .withEnv("discovery.type", "single-node")
          .withEnv("xpack.security.enabled", "false")
          .withEnv("ES_JAVA_OPTS", "-Xms512m -Xmx512m")
          .withExposedPorts(9200)
          .waitingFor(Wait.forHttp("/_cluster/health").forStatusCode(200).withStartupTimeout(Duration.ofMinutes(3)));

  private static RestClient restClient;
  private static ElasticsearchClient client;

  private final JobElasticsearchRepository repository = new JobElasticsearchRepository(client, properties());

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
        .settings(settings -> settings.analysis(analysis -> analysis.analyzer("vi_text", analyzer -> analyzer
            .custom(custom -> custom.tokenizer("standard").filter("lowercase", "asciifolding")))))
        .mappings(mappings -> mappings
            .properties("title", property -> property.text(text -> text.analyzer("vi_text")))
            .properties("posted_date", property -> property.date(date -> date))
            .properties("expired_date", property -> property.date(date -> date))));
  }

  @Test
  void findsOpenJobsWithEveryWordOfATitleIgnoringAccentsAndOrder() throws IOException {
    UUID open = job("Nhân viên Kế Toán Tổng Hợp", NOW, NOW.plus(Duration.ofDays(1)));
    UUID noExpiry = job("Kế toán trưởng", NOW.minusSeconds(60), null);
    job("Kế toán", NOW, NOW.minus(Duration.ofDays(1)));
    job("Kế hoạch sản xuất", NOW, null);

    assertThat(repository.findOpenByTitles(List.of(new TitleQuery("ke toan", 1f)), 10))
        .containsExactly(new TitleHit(open, "Nhân viên Kế Toán Tổng Hợp"), new TitleHit(noExpiry, "Kế toán trưởng"));
  }

  @Test
  void betterTitlesComeFirstThenNewerJobs() throws IOException {
    UUID past = job("Tester", NOW, null);
    UUID olderCurrent = job("Java Backend Developer", NOW.minus(Duration.ofDays(2)), null);
    UUID newerCurrent = job("Backend Developer", NOW.minus(Duration.ofDays(1)), null);
    List<TitleQuery> titles = List.of(new TitleQuery("tester", 1f), new TitleQuery("backend developer", 2f));

    assertThat(repository.findOpenByTitles(titles, 10)).extracting(TitleHit::jobId)
        .containsExactly(newerCurrent, olderCurrent, past);
    assertThat(repository.findOpenByTitles(titles, 1)).extracting(TitleHit::jobId).containsExactly(newerCurrent);
  }

  private static UUID job(String title, Instant posted, Instant expires) throws IOException {
    UUID id = UUID.randomUUID();
    Map<String, Object> document = new HashMap<>(Map.of("title", title, "posted_date", posted.toString()));
    if (expires != null) {
      document.put("expired_date", expires.toString());
    }
    client.index(request -> request.index(INDEX).id(id.toString()).document(document).refresh(Refresh.True));
    return id;
  }

  private static ElasticsearchProperties properties() {
    ElasticsearchProperties properties = new ElasticsearchProperties();
    properties.setIndex(INDEX);
    return properties;
  }
}
