package com.jobbeacon.utils;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UuidUtilsTest {

  @Test
  void timestampOfV7IsCreationTime() {
    Instant before = Instant.now().minusMillis(1);
    UUID id = UuidUtils.v7();
    Instant after = Instant.now().plusMillis(1);

    Instant created = UuidUtils.timestampOf(id);

    assertTrue(!created.isBefore(before) && !created.isAfter(after), created.toString());
  }

  @Test
  void timestampOfOtherVersionsIsNull() {
    assertNull(UuidUtils.timestampOf(UUID.randomUUID()));
    assertNull(UuidUtils.timestampOf(null));
  }

  @Test
  void lowerBoundSortsBeforeIdsCreatedLater() {
    Instant since = Instant.now().minus(Duration.ofDays(7));
    UUID bound = UuidUtils.v7LowerBound(since);
    UUID now = UuidUtils.v7();

    assertEquals(7, bound.version());
    assertEquals(since.toEpochMilli(), UuidUtils.timestampOf(bound).toEpochMilli());
    // Postgres compares uuids byte by byte, i.e. as unsigned most-significant bits first
    assertTrue(Long.compareUnsigned(bound.getMostSignificantBits(), now.getMostSignificantBits()) < 0);
  }
}
