package com.jobbeacon.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.geocode")
public record GeocodeProperties(
        boolean enabled,
        String baseUrl,
        String userAgent,
        Duration minInterval,
        Duration timeout,
        Duration positiveTtl,
        Duration negativeTtl
) {
}
