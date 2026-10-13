package com.jobbeacon.service;

import com.jobbeacon.dto.skill.SkillRef;
import java.util.Map;
import java.util.Optional;

public interface SkillLookupService {

  /**
   * Looks up an existing skill by name (trimmed, case-insensitive). Never creates a skill.
   * When several rows share the same name, the one with the smallest id is returned.
   */
  Optional<SkillRef> findByName(String raw);

  /**
   * All skills keyed by lowercase, trimmed name. Cached in memory with a short TTL.
   * On duplicate names the skill with the smallest id wins. The returned map is unmodifiable.
   */
  Map<String, SkillRef> nameIndex();
}
