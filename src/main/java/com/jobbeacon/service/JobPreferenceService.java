package com.jobbeacon.service;

import com.jobbeacon.dto.request.JobPreferenceRequest;
import com.jobbeacon.dto.response.JobPreferenceResponse;
import java.util.UUID;

/** Job preference of a user (one per user, D-010), keyed by the local {@code User} id. */
public interface JobPreferenceService {

  /** The saved preference, or the defaults (with a null {@code id}) when none exists; never writes. */
  JobPreferenceResponse getPreference(UUID userId);

  /**
   * Creates or fully replaces the user's preference. Invalid enum values, an unknown province or
   * {@code expected_salary_min > expected_salary_max} raise {@code BadRequestException}. A concurrent
   * first insert hitting {@code uq_user_job_preference_user} is retried once.
   */
  JobPreferenceResponse updatePreference(UUID userId, JobPreferenceRequest request);
}
