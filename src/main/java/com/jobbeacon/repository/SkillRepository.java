package com.jobbeacon.repository;

import com.jobbeacon.entity.Skill;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SkillRepository extends BaseRepository<Skill> {
    Optional<Skill> findByNameIgnoreCase(String name);
}
