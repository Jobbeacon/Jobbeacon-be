package com.jobbeacon.repository;

import com.jobbeacon.entity.SkillAlias;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SkillAliasRepository extends BaseRepository<SkillAlias> {
    Optional<SkillAlias> findByNormalizedAlias(String normalizedAlias);
}
