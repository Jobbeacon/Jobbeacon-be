package com.jobbeacon.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Market statistics settings.
 *
 * @param usdVndRate      VND per USD, used to put USD salaries on the same scale as VND ones
 * @param minSalarySample groups with fewer salaried jobs than this report no salary figures
 */
@ConfigurationProperties(prefix = "app.market")
public record MarketProperties(double usdVndRate, int minSalarySample) {
}
