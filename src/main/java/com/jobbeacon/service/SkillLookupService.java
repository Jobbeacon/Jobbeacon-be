package com.jobbeacon.service;

import com.jobbeacon.dto.skill.SkillRef;
import java.util.List;
import java.util.Map;

public interface SkillLookupService {

  /**
   * Gazetteer of known skill names, keyed by {@code SkillUtils.normalizeSkill}: every skill's
   * {@code normalized_name} (its {@code name} normalized when null) plus every
   * {@code skill_alias.normalized_alias}, an alias mapping to the {@link SkillRef} of its skill.
   * On duplicate keys the skill with the smallest id wins, and skill names win over aliases.
   * Cached in memory with a short TTL. Never creates a skill. The returned map is unmodifiable.
   */
  Map<String, SkillRef> nameIndex();

  /**
   * Searches {@link #nameIndex()} for autocomplete. {@code q} is normalized with
   * {@code SkillUtils.normalizeSkill}; blank yields an empty list. Ranking: exact key match, then
   * prefix, then contains; ties broken by shorter skill name, then name. Results are grouped by skill
   * id (aliases resolve to their skill, keeping the best rank) and carry the skill's own name.
   * {@code limit} is clamped to 1..20. Never queries the database beyond the cached index.
   */
  List<SkillRef> search(String q, int limit);
}
