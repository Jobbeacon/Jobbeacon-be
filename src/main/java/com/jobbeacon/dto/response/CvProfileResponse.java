package com.jobbeacon.dto.response;

import com.jobbeacon.dto.cv.CvParsedPayload;
import com.jobbeacon.entity.CvProfile;
import com.jobbeacon.service.CvProfileService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * {@code hasCv} ({@code has_cv}) is false for an empty profile created by adding a skill manually
 * (D-011); its CV fields are then null and experiences/educations empty.
 */
public record CvProfileResponse(
    UUID id,
    boolean hasCv,
    String currentTitle,
    String summary,
    Integer totalMonthsExperience,
    List<CvParsedPayload.Experience> experiences,
    List<CvParsedPayload.Education> educations,
    Instant parsedAt,
    Instant updatedAt,
    List<CvProfileSkillResponse> skills
) {

  public static CvProfileResponse from(CvProfileService.CvProfileView view) {
    CvProfile profile = view.profile();
    List<CvProfileService.SkillEntry> skills = view.skills() == null ? List.of() : view.skills();
    return new CvProfileResponse(
        profile.getId(),
        profile.getParsedAt() != null,
        profile.getCurrentTitle(),
        profile.getSummary(),
        profile.getTotalMonthsExperience(),
        profile.getExperiences() == null ? List.of() : profile.getExperiences(),
        profile.getEducations() == null ? List.of() : profile.getEducations(),
        profile.getParsedAt(),
        profile.getUpdatedAt(),
        skills.stream().map(CvProfileSkillResponse::from).toList()
    );
  }
}
