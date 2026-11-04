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

  /**
   * Profile with its skill mappings; {@code cv_profile_not_found} if there is no row. The profile may
   * be an empty one created by {@link #addSkill} (D-011): no CV fields, {@code parsedAt} null.
   */
  CvProfileView getProfile(UUID userId);

  /**
   * Deletes the profile (mappings cascade, including manually added skills);
   * {@code cv_profile_not_found} if there is none.
   */
  void deleteProfile(UUID userId);

  /**
   * Skill mappings of the user's profile. Never throws for a missing profile: returns a
   * {@code CvProfileSkills} with a null {@code profileId} and an empty list instead.
   */
  CvProfileSkills getSkills(UUID userId);

  /**
   * Removes one skill from the user's profile. {@code cv_profile_not_found} if there is no profile,
   * {@code cv_profile_skill_not_found} if the skill is not mapped to it. A new CV extraction replaces
   * all mappings, so a removed skill can come back.
   */
  void deleteSkill(UUID userId, UUID skillId);

  /**
   * Adds an existing skill picked by the user to their profile (D-011), creating an empty profile
   * (only {@code user_id}) if there is none yet. Runs in its own transaction and retries once on a
   * concurrent insert. The new mapping has no confidence/experience/evidence. A new CV extraction
   * replaces all mappings, including manual ones.
   *
   * @return the profile's skills after adding
   * @throws com.jobbeacon.exception.base.NotFoundException {@code skill_not_found}
   * @throws com.jobbeacon.exception.base.ConflictException {@code cv_profile_skill_exists}
   */
  CvProfileSkills addSkill(UUID userId, UUID skillId);

  record CvProfileView(CvProfile profile, List<SkillEntry> skills) {
  }

  /** {@code profileId} is null when the user has no profile yet. */
  record CvProfileSkills(UUID profileId, List<SkillEntry> skills) {
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
