package com.jobbeacon.dto.response.admin;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AdminJobRowResponse(
    UUID id,
    String title,
    UUID companyId,
    String companyName,
    List<String> locations,
    Long minSalary,
    Long maxSalary,
    String currency,
    String salaryPeriod,
    String employmentType,
    Instant postedDate,
    Instant expiredDate,
    List<String> sources,
    Instant lastCrawledAt
) {
}
