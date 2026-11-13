package com.jobbeacon.service.admin.impl;

import com.jobbeacon.dto.response.admin.AdminOverviewResponse;
import com.jobbeacon.repository.admin.AdminOverviewQueryRepository;
import com.jobbeacon.repository.admin.AdminOverviewQueryRepository.SourceCount;
import com.jobbeacon.utils.AdminTime;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdminOverviewServiceImplTest {

  @Test
  void fillsEveryDayAndSourceAndFindsQuietSources() {
    LocalDate today = AdminTime.today();
    AdminOverviewQueryRepository repository = mock(AdminOverviewQueryRepository.class);
    when(repository.sources()).thenReturn(List.of("topcv.vn", "itviec.com"));
    when(repository.countByDay(any())).thenReturn(List.of(
        new SourceCount(today.toString(), "topcv.vn", 5),
        new SourceCount(today.minusDays(2).toString(), "itviec.com", 3)));
    when(repository.countByMonth(any())).thenReturn(List.of(
        new SourceCount(YearMonth.from(today).toString(), "topcv.vn", 40)));
    when(repository.countByHour(any())).thenReturn(List.of(
        new SourceCount("9", "topcv.vn", 4), new SourceCount("9", "itviec.com", 2)));
    when(repository.recent(anyInt())).thenReturn(List.of());

    AdminOverviewResponse overview = new AdminOverviewServiceImpl(repository).overview(7);

    assertEquals(7, overview.daily().size());
    assertEquals(today, overview.daily().get(6).date());
    assertEquals(0L, overview.daily().get(6).bySource().get("itviec.com"));
    assertEquals(8, overview.totalWindow());
    assertEquals(5, overview.totalToday());
    assertEquals(40, overview.totalMonth());
    assertEquals(6, overview.monthly().size());
    assertEquals(24, overview.hours().size());
    assertEquals(6L, overview.hours().get(9));
    assertEquals(List.of("itviec.com"), overview.quietSources());
  }
}
