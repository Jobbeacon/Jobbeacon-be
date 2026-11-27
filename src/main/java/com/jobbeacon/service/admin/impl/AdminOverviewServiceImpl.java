package com.jobbeacon.service.admin.impl;

import com.jobbeacon.dto.response.admin.AdminOverviewResponse;
import com.jobbeacon.dto.response.admin.AdminOverviewResponse.DayCount;
import com.jobbeacon.dto.response.admin.AdminOverviewResponse.MonthCount;
import com.jobbeacon.repository.admin.AdminOverviewQueryRepository;
import com.jobbeacon.repository.admin.AdminOverviewQueryRepository.SourceCount;
import com.jobbeacon.service.admin.AdminOverviewService;
import com.jobbeacon.utils.AdminTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AdminOverviewServiceImpl implements AdminOverviewService {

  private static final int MAX_DAYS = 366;
  private static final int MONTHS = 6;
  private static final int RECENT = 12;

  private final AdminOverviewQueryRepository repository;

  @Override
  @Transactional(readOnly = true)
  public AdminOverviewResponse overview(int days) {
    int window = Math.max(1, Math.min(days, MAX_DAYS));
    LocalDate today = AdminTime.today();
    LocalDate firstDay = today.minusDays(window - 1L);
    YearMonth thisMonth = YearMonth.from(today);
    YearMonth firstMonth = thisMonth.minusMonths(MONTHS - 1L);

    List<String> sources = new ArrayList<>(repository.sources());
    List<SourceCount> dayRows = repository.countByDay(AdminTime.startOf(firstDay));
    List<SourceCount> monthRows = repository.countByMonth(AdminTime.startOf(firstMonth.atDay(1)));
    List<SourceCount> hourRows = repository.countByHour(AdminTime.startOf(firstDay));
    for (SourceCount row : dayRows) {
      if (row.domain() != null && !sources.contains(row.domain())) {
        sources.add(row.domain());
      }
    }

    Map<String, Map<String, Long>> byDay = group(dayRows);
    List<DayCount> daily = new ArrayList<>();
    long totalWindow = 0;
    for (LocalDate date = firstDay; !date.isAfter(today); date = date.plusDays(1)) {
      Map<String, Long> bySource = fill(sources, byDay.get(date.toString()));
      long total = sum(bySource);
      totalWindow += total;
      daily.add(new DayCount(date, total, bySource));
    }

    Map<String, Map<String, Long>> byMonth = group(monthRows);
    List<MonthCount> monthly = new ArrayList<>();
    for (YearMonth month = firstMonth; !month.isAfter(thisMonth); month = month.plusMonths(1)) {
      Map<String, Long> bySource = fill(sources, byMonth.get(month.toString()));
      monthly.add(new MonthCount(month.toString(), sum(bySource), bySource));
    }

    long[] hourCounts = new long[24];
    for (SourceCount row : hourRows) {
      int hour = Integer.parseInt(row.key());
      if (hour >= 0 && hour < 24) {
        hourCounts[hour] += row.count();
      }
    }
    List<Long> hours = new ArrayList<>(24);
    for (long count : hourCounts) {
      hours.add(count);
    }

    DayCount todayCount = daily.get(daily.size() - 1);
    List<String> quiet = sources.stream()
        .filter(source -> todayCount.bySource().getOrDefault(source, 0L) == 0L)
        .toList();

    return new AdminOverviewResponse(
        sources,
        today,
        totalWindow,
        todayCount.total(),
        monthly.get(monthly.size() - 1).total(),
        daily,
        monthly,
        hours,
        quiet,
        repository.recent(RECENT)
    );
  }

  private static Map<String, Map<String, Long>> group(List<SourceCount> rows) {
    Map<String, Map<String, Long>> grouped = new HashMap<>();
    for (SourceCount row : rows) {
      grouped.computeIfAbsent(row.key(), key -> new HashMap<>()).merge(row.domain(), row.count(), Long::sum);
    }
    return grouped;
  }

  private static Map<String, Long> fill(List<String> sources, Map<String, Long> counts) {
    Map<String, Long> filled = new LinkedHashMap<>();
    for (String source : sources) {
      filled.put(source, counts == null ? 0L : counts.getOrDefault(source, 0L));
    }
    return filled;
  }

  private static long sum(Map<String, Long> counts) {
    return counts.values().stream().mapToLong(Long::longValue).sum();
  }
}
