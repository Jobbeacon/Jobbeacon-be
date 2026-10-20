package com.jobbeacon.service;

import com.jobbeacon.entity.Skill;

import java.util.List;
import java.util.Map;
import java.util.Set;

public interface SkillResolutionService {

    /**
     * Resolves every raw skill, creating skills/aliases as needed. Same as
     * {@code resolveSkillMap(rawSkills, Integer.MAX_VALUE)} without the keys.
     */
    Set<Skill> resolveSkills(List<String> rawSkills);

    /**
     * Resolves raw skill names: exact {@code normalized_name}/alias match first, otherwise
     * embedding + LLM classification (alias of an existing skill or a new skill).
     *
     * @param rawSkills     raw names; blank and duplicate (after normalization) entries are skipped
     * @param maxNewLookups max number of skills allowed to take the embedding + LLM path; the
     *                      others without an exact match are skipped (nothing created, no Gemini call)
     * @return resolved skills keyed by {@code SkillUtils.normalizeSkill(raw)}; raws that could not be
     * resolved have no key. Skills are managed by the caller's persistence context when called
     * inside a transaction.
     */
    Map<String, Skill> resolveSkillMap(List<String> rawSkills, int maxNewLookups);
}
