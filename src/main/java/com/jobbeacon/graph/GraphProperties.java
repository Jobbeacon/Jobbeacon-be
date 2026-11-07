package com.jobbeacon.graph;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.graph")
public record GraphProperties(
        boolean enabled,
        String database,
        int batchSize,
        int maxAttempts
) {
}
