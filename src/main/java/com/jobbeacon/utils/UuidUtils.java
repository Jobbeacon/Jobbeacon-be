package com.jobbeacon.utils;

import com.fasterxml.uuid.Generators;
import java.util.UUID;

public final class UuidUtils {

  private UuidUtils() {
  }

  public static UUID v7() {
    return Generators.timeBasedEpochGenerator().generate();
  }
}
