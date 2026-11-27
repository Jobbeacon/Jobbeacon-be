package com.jobbeacon.repository.projection;

import java.util.UUID;

/** The parts of a job that are compared with a user's job preference and CV. */
public record JobPreferenceFacts(
        UUID jobId,
        String employmentType,
        Long minSalary,
        Long maxSalary,
        String currency,
        String salaryPeriod,
        Integer experienceMonths
) {
}
