package com.jobbeacon.dto.response.admin;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AdminJobDetailResponse(
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
    Instant lastCrawledAt,
    String description,
    String benefits,
    Integer experienceMonths,
    List<NamedRef> skills,
    List<CategoryRef> categories,
    List<Mapping> mappings
) {

  public record NamedRef(UUID id, String name) {
  }

  public record CategoryRef(UUID id, String name, List<String> path) {
  }

  public record Mapping(String domain, String jobUrl, Instant crawledAt) {
  }
}
