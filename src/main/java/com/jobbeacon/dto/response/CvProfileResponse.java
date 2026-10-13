package com.jobbeacon.dto.response;

import com.jobbeacon.dto.cv.CvParsedPayload;
import com.jobbeacon.entity.CvProfile;
import com.jobbeacon.service.CvProfileService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CvProfileResponse(
    UUID id,
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
