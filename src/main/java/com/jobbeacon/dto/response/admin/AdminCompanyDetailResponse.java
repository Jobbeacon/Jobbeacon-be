package com.jobbeacon.dto.response.admin;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AdminCompanyDetailResponse(
    UUID id,
    String name,
    String logoUrl,
    long jobCount,
    long openJobCount,
    List<String> sources,
    Instant createdAt,
    Instant lastCrawledAt,
    List<String> locations,
    List<Duplicate> possibleDuplicates
) {

  public record Duplicate(UUID id, String name, long jobCount) {
  }
}
