package com.jobbeacon.repository.admin;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/** Small JDBC helpers shared by the admin query repositories. */
final class AdminSql {

  private AdminSql() {
  }

  /** Pattern for ILIKE "contains", with LIKE wildcards in the user's text escaped. Null when blank. */
  static String containsPattern(String q) {
    if (q == null || q.isBlank()) {
      return null;
    }
    String escaped = q.trim()
        .replace("\\", "\\\\")
        .replace("%", "\\%")
        .replace("_", "\\_");
    return "%" + escaped + "%";
  }

  static String lower(String value) {
    return value == null ? null : value.trim().toLowerCase(Locale.ROOT);
  }

  static Timestamp ts(Instant instant) {
    return instant == null ? null : Timestamp.from(instant);
  }

  static Instant instant(ResultSet rs, String column) throws SQLException {
    Timestamp value = rs.getTimestamp(column);
    return value == null ? null : value.toInstant();
  }

  static UUID uuid(ResultSet rs, String column) throws SQLException {
    return rs.getObject(column, UUID.class);
  }

  static Long longOrNull(ResultSet rs, String column) throws SQLException {
    long value = rs.getLong(column);
    return rs.wasNull() ? null : value;
  }

  static Integer intOrNull(ResultSet rs, String column) throws SQLException {
    int value = rs.getInt(column);
    return rs.wasNull() ? null : value;
  }

  static List<String> strings(ResultSet rs, String column) throws SQLException {
    Array array = rs.getArray(column);
    if (array == null) {
      return List.of();
    }
    Object[] values = (Object[]) array.getArray();
    return Arrays.stream(values).filter(Objects::nonNull).map(Object::toString).toList();
  }

  static List<String> sortedStrings(ResultSet rs, String column) throws SQLException {
    return strings(rs, column).stream().sorted(String.CASE_INSENSITIVE_ORDER).toList();
  }
}
