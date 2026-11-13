package com.jobbeacon.dto.response.admin;

import com.jobbeacon.constant.UserRole;
import com.jobbeacon.constant.UserStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AdminUserDetailResponse(
    UUID id,
    String email,
    String displayName,
    String avatarUrl,
    UserRole role,
    UserStatus status,
    boolean hasCv,
    Instant createdAt,
    Instant lastLoginAt,
    Cv cv,
    Preference preference
) {

  public record Cv(String currentTitle, Integer totalMonthsExperience, List<String> skills, Instant updatedAt) {
  }

  public record Preference(
      List<String> desiredTitles,
      List<String> employmentTypes,
      List<String> provinces,
      Long expectedSalaryMin,
      Long expectedSalaryMax,
      String currency,
      String salaryPeriod,
      boolean salaryNegotiable,
      boolean remoteOk,
      boolean openToWork
  ) {
  }
}
