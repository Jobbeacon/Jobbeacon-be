package com.jobbeacon.dto.request;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import lombok.Data;

/** Skill picked by the user (from {@code GET /api/v1/skills}) to add to their CV profile (D-011). */
@Data
public class AddCvSkillRequest {

  @NotNull(message = "cv_profile_skill_id_required")
  private UUID skillId;
}
