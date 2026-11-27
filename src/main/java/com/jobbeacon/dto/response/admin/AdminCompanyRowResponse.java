package com.jobbeacon.dto.response.admin;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AdminCompanyRowResponse(
    UUID id,
    String name,
    String logoUrl,
    long jobCount,
    long openJobCount,
    List<String> sources,
    Instant createdAt,
    Instant lastCrawledAt
) {
}
