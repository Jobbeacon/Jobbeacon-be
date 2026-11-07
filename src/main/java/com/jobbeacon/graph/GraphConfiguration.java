package com.jobbeacon.graph;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@EnableConfigurationProperties(GraphProperties.class)
@ConditionalOnProperty(name = "app.graph.enabled", havingValue = "true")
public class GraphConfiguration {
}
