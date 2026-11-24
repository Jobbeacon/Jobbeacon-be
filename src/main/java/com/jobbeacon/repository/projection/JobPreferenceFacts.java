package com.jobbeacon.repository.projection;

import java.util.UUID;

/** The parts of a job that are compared with a user's job preference. */
public record JobPreferenceFacts(
        UUID jobId,
        String title,
        String employmentType,
        Long minSalary,
        Long maxSalary,
        String currency,
        String salaryPeriod
) {
}
