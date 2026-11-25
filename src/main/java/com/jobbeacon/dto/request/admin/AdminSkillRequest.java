package com.jobbeacon.dto.request.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class AdminSkillRequest {

  @NotBlank(message = "admin_skill_name_required")
  @Size(max = 255, message = "admin_skill_name_too_long")
  private String name;

  /** Full replacement of the skill's other names. */
  @Size(max = 50, message = "admin_skill_too_many_aliases")
  private List<@Size(max = 255, message = "admin_skill_name_too_long") String> aliases;
}
