package com.jobbeacon.dto.response.admin;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record AdminOverviewResponse(
    List<String> sources,
    LocalDate today,
    long totalWindow,
    long totalToday,
    long totalMonth,
    List<DayCount> daily,
    List<MonthCount> monthly,
    List<Long> hours,
    List<String> quietSources,
    List<RecentCrawl> recent
) {

  public record DayCount(LocalDate date, long total, Map<String, Long> bySource) {
  }

  public record MonthCount(String month, long total, Map<String, Long> bySource) {
  }

  public record RecentCrawl(
      Instant crawledAt,
      String domain,
      String jobUrl,
      UUID jobId,
      String jobTitle,
      UUID companyId,
      String companyName
  ) {
  }
}
