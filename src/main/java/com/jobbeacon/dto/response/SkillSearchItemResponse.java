package com.jobbeacon.dto.response;

import com.jobbeacon.dto.skill.SkillRef;
import java.util.UUID;

public record SkillSearchItemResponse(UUID id, String name) {

  public static SkillSearchItemResponse from(SkillRef ref) {
    return new SkillSearchItemResponse(ref.id(), ref.name());
  }
}
