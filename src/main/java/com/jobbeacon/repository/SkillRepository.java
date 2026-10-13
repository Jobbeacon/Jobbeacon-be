package com.jobbeacon.repository;

import com.jobbeacon.dto.skill.SkillRef;
import com.jobbeacon.entity.Skill;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SkillRepository extends BaseRepository<Skill> {
    Optional<Skill> findByNameIgnoreCase(String name);

    Optional<Skill> findFirstByNameIgnoreCaseOrderByIdAsc(String name);

    @Query("select new com.jobbeacon.dto.skill.SkillRef(s.id, s.name) from Skill s"
            + " where s.name is not null order by s.id asc")
    List<SkillRef> findAllRefsOrderByIdAsc();
}
