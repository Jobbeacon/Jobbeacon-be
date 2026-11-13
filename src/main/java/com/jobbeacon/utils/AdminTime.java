package com.jobbeacon.utils;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/** Calendar boundaries for admin statistics, in Vietnam time. */
public final class AdminTime {

  public static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
  public static final String ZONE_ID = "Asia/Ho_Chi_Minh";

  private AdminTime() {
  }

  public static LocalDate today() {
    return LocalDate.now(ZONE);
  }

  public static Instant startOf(LocalDate date) {
    return date.atStartOfDay(ZONE).toInstant();
  }

  public static Instant startOfToday() {
    return startOf(today());
  }

  public static Instant startOfMonth() {
    return startOf(today().withDayOfMonth(1));
  }
}
