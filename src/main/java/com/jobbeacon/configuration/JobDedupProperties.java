package com.jobbeacon.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.job-dedup")
public record JobDedupProperties(
        boolean enabled,
        double jaccardThreshold,
        double titleThreshold,
        double salaryTolerance,
        double highContentThreshold,
        double relaxedTitleThreshold,
        double noTitleThreshold,
        double containmentThreshold,
        double containmentTitleThreshold,
        int containmentMinShingles,
        int shortTextShingles
) {
    public JobDedupProperties {
        if (highContentThreshold <= 0) {
            highContentThreshold = 0.9;
        }
        if (relaxedTitleThreshold <= 0) {
            relaxedTitleThreshold = 0.3;
        }
        if (noTitleThreshold <= 0) {
            noTitleThreshold = 0.95;
        }
        if (containmentThreshold <= 0) {
            containmentThreshold = 0.85;
        }
        if (containmentTitleThreshold <= 0) {
            containmentTitleThreshold = 0.6;
        }
        if (containmentMinShingles <= 0) {
            containmentMinShingles = 100;
        }
        if (shortTextShingles <= 0) {
            shortTextShingles = 100;
        }
    }
}
