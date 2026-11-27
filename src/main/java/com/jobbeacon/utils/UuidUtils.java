package com.jobbeacon.utils;

import com.fasterxml.uuid.Generators;
import java.time.Instant;
import java.util.UUID;

public final class UuidUtils {

  private UuidUtils() {
  }

  public static UUID v7() {
    return Generators.timeBasedEpochGenerator().generate();
  }

  /**
   * Creation time embedded in a UUID v7 (first 48 bits = Unix epoch millis). Null for other versions.
   */
  public static Instant timestampOf(UUID uuid) {
    if (uuid == null || uuid.version() != 7) {
      return null;
    }
    return Instant.ofEpochMilli(uuid.getMostSignificantBits() >>> 16);
  }

  /**
   * Smallest UUID v7 that can be generated at {@code instant}; every v7 id created at or after it
   * compares greater or equal (byte order), which lets SQL filter "created since" on the primary key.
   */
  public static UUID v7LowerBound(Instant instant) {
    long msb = (instant.toEpochMilli() << 16) | 0x7000L;
    return new UUID(msb, 0x8000000000000000L);
  }
}
