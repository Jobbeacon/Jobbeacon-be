package com.jobbeacon.dto.response;

import com.jobbeacon.service.CvProfileService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CvProfileSkillResponse(
    UUID skillId,
    String name,
    BigDecimal confidence,
    Integer experienceMonths,
    LocalDate lastUsedAt
) {

  public static CvProfileSkillResponse from(CvProfileService.SkillEntry entry) {
    return new CvProfileSkillResponse(
        entry.skillId(),
        entry.skillName(),
        entry.confidence(),
        entry.experienceMonths(),
        entry.lastUsedAt()
    );
  }
}
