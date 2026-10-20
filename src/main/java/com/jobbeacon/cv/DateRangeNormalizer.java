package com.jobbeacon.cv;

import java.text.Normalizer;
import java.time.Month;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses free-form CV dates into {@link YearMonth}. Accepted inputs (case-insensitive):
 * {@code YYYY}, {@code MM/YYYY}, {@code M/YYYY}, {@code MM-YYYY}, {@code MM.YYYY}, {@code YYYY-MM},
 * {@code YYYY/MM}, {@code YYYY-MM-DD}, {@code DD/MM/YYYY}, {@code Mar 2020}, {@code March 2020},
 * {@code Tháng 3/2020}, {@code tháng 3 năm 2020}, {@code T3/2020}. A year-only value maps to
 * January when it starts a range and December when it ends one.
 */
public final class DateRangeNormalizer {

  public static final DateTimeFormatter OUTPUT = DateTimeFormatter.ofPattern("yyyy-MM");

  private static final int MIN_YEAR = 1950;
  private static final int MAX_YEAR = 2100;

  private static final Set<String> PRESENT_WORDS = Set.of(
      "nay", "hiện tại", "hiện nay", "đến nay", "tới nay", "đến hiện tại", "present", "now",
      "current", "currently", "today", "ongoing", "to date", "till now", "until now");

  private static final Map<String, Integer> MONTH_NAMES = Map.ofEntries(
      Map.entry("jan", 1), Map.entry("feb", 2), Map.entry("mar", 3), Map.entry("apr", 4),
      Map.entry("may", 5), Map.entry("jun", 6), Map.entry("jul", 7), Map.entry("aug", 8),
      Map.entry("sep", 9), Map.entry("sept", 9), Map.entry("oct", 10), Map.entry("nov", 11),
      Map.entry("dec", 12));

  private static final Pattern YEAR_ONLY = Pattern.compile("^(\\d{4})$");
  private static final Pattern YEAR_MONTH = Pattern.compile("^(\\d{4})\\s*[-/.]\\s*(\\d{1,2})(?:\\s*[-/.]\\s*\\d{1,2})?$");
  private static final Pattern MONTH_YEAR = Pattern.compile("^(?:\\d{1,2}\\s*[-/.]\\s*)?(\\d{1,2})\\s*[-/.]\\s*(\\d{4})$");
  private static final Pattern VI_MONTH_YEAR = Pattern.compile("^(?:tháng|th|t)\\.?\\s*(\\d{1,2})\\s*(?:[-/.,]|năm|\\s)\\s*(?:năm\\s*)?(\\d{4})$");
  private static final Pattern NAMED_MONTH_YEAR = Pattern.compile("^([a-z]{3,9})\\.?\\s*,?\\s*(\\d{4})$");

  private DateRangeNormalizer() {
  }

  /** True when the value means "until now" (nay, hiện tại, present, now, ...). */
  public static boolean isPresent(String value) {
    if (value == null) {
      return false;
    }
    return PRESENT_WORDS.contains(clean(value));
  }

  /**
   * Parses a single date. Returns empty for null/blank, present words and unparseable values;
   * callers distinguish those with {@link #isPresent(String)} and a blank check.
   */
  public static Optional<YearMonth> parse(String value, boolean endOfRange) {
    if (value == null || value.isBlank() || isPresent(value)) {
      return Optional.empty();
    }
    String s = clean(value);

    Matcher m = YEAR_ONLY.matcher(s);
    if (m.matches()) {
      return of(Integer.parseInt(m.group(1)), endOfRange ? 12 : 1);
    }
    m = YEAR_MONTH.matcher(s);
    if (m.matches()) {
      return of(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)));
    }
    m = MONTH_YEAR.matcher(s);
    if (m.matches()) {
      return of(Integer.parseInt(m.group(2)), Integer.parseInt(m.group(1)));
    }
    m = VI_MONTH_YEAR.matcher(s);
    if (m.matches()) {
      return of(Integer.parseInt(m.group(2)), Integer.parseInt(m.group(1)));
    }
    m = NAMED_MONTH_YEAR.matcher(s);
    if (m.matches()) {
      Integer month = monthFromName(m.group(1));
      return month == null ? Optional.empty() : of(Integer.parseInt(m.group(2)), month);
    }
    return Optional.empty();
  }

  public static String format(YearMonth value) {
    return value == null ? null : value.format(OUTPUT);
  }

  private static Integer monthFromName(String name) {
    if (name.length() > 3 && !"sept".equals(name)) {
      Integer month = MONTH_NAMES.get(name.substring(0, 3));
      if (month == null) {
        return null;
      }
      String full = Month.of(month).name().toLowerCase(Locale.ROOT);
      return full.equals(name) ? month : null;
    }
    return MONTH_NAMES.get(name);
  }

  private static Optional<YearMonth> of(int year, int month) {
    if (year < MIN_YEAR || year > MAX_YEAR || month < 1 || month > 12) {
      return Optional.empty();
    }
    return Optional.of(YearMonth.of(year, month));
  }

  private static String clean(String value) {
    return Normalizer.normalize(value, Normalizer.Form.NFC)
        .trim()
        .toLowerCase(Locale.ROOT)
        .replaceAll("\\s+", " ");
  }
}
