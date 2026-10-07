package com.jobbeacon.search;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ElasticsearchClientConfig {

    @Bean(destroyMethod = "close")
    RestClient elasticsearchRestClient(ElasticsearchProperties properties) {
        return RestClient.builder(HttpHost.create(properties.getUrl()))
                .setRequestConfigCallback(config -> config.setConnectTimeout(5_000).setSocketTimeout(120_000))
                .build();
    }

    @Bean
    ElasticsearchClient elasticsearchClient(RestClient elasticsearchRestClient) {
        RestClientTransport transport = new RestClientTransport(elasticsearchRestClient, new JacksonJsonpMapper());
        return new ElasticsearchClient(transport);
    }
}
