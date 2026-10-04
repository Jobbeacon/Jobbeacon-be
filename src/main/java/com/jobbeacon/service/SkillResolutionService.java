package com.jobbeacon.service;

import com.jobbeacon.entity.Skill;

import java.util.List;
import java.util.Set;

public interface SkillResolutionService {
    Set<Skill> resolveSkills(List<String> rawSkills);
}
