package com.jobbeacon.dto.response;

import java.util.UUID;

public record JobSummaryResponse(
        UUID id,
        String title,
        String companyName,
        Long minSalary,
        Long maxSalary,
        String location,
        String currency
) {
}