package com.jobbeacon.dto.response;

import java.util.List;
import java.util.UUID;

public record CompanySummaryResponse(
        UUID id,
        String name,
        String logoUrl,
        long openJobsCount,
        List<String> locations,
        List<String> sources
) {
}
