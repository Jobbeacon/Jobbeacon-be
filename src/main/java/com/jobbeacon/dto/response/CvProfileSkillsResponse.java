package com.jobbeacon.dto.response;

import com.jobbeacon.service.CvProfileService;
import java.util.List;
import java.util.UUID;

public record CvProfileSkillsResponse(
    UUID profileId,
    List<CvProfileSkillResponse> skills
) {

  public static CvProfileSkillsResponse from(CvProfileService.CvProfileSkills profileSkills) {
    List<CvProfileService.SkillEntry> skills =
        profileSkills.skills() == null ? List.of() : profileSkills.skills();
    return new CvProfileSkillsResponse(
        profileSkills.profileId(),
        skills.stream().map(CvProfileSkillResponse::from).toList()
    );
  }
}
