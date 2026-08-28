package com.jobbeacon.utils;

import java.time.LocalDate;

public final class DateUtils {

  private DateUtils() {
  }

  public static String getCurrentDateString() {
    return LocalDate.now().toString();
  }
}
