package com.jobbeacon.service;

import com.jobbeacon.dto.cv.CvParsedPayload;
import com.jobbeacon.entity.CvProfile;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * CV profile of a user (one per user), written by the extraction pipeline (D-007).
 * All methods are keyed by the local {@code User} id.
 */
public interface CvProfileService {

  /**
   * Upserts the user's profile from a successfully parsed CV and replaces all its skill mappings,
   * in its own transaction (last write wins). A concurrent first insert for the same user that hits
   * {@code uq_cv_profile_user} is retried once. Publishes {@code CvProfilePublishedEvent}.
   *
   * @param parserModelVersion model name, or {@code deterministic-v1} when no model was used
   */
  CvProfileView saveParsed(UUID userId, CvParsedPayload payload, String parserModelVersion);

  /** Profile with its skill mappings; {@code cv_profile_not_found} otherwise. */
  CvProfileView getProfile(UUID userId);

  /** Deletes the profile (mappings cascade); {@code cv_profile_not_found} if there is none. */
  void deleteProfile(UUID userId);

  record CvProfileView(CvProfile profile, List<SkillEntry> skills) {
  }

  record SkillEntry(
      UUID skillId,
      String skillName,
      BigDecimal confidence,
      Integer experienceMonths,
      LocalDate lastUsedAt,
      String evidenceText
  ) {
  }
}
