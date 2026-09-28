package com.jobbeacon.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record JobSummaryResponse(
        UUID id,
        String title,
        String companyName,
        Long minSalary,
        Long maxSalary,
        List<String> location,
        String currency,
        String companyLogoUrl,
        Instant postedDate
) {
}