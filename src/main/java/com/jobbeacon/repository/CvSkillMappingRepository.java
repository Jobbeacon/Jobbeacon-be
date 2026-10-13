package com.jobbeacon.repository;

import com.jobbeacon.entity.CvSkillMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Repository
public interface CvSkillMappingRepository extends JpaRepository<CvSkillMapping, CvSkillMapping.CvSkillMappingId> {

    List<CvSkillMapping> findByIdCvProfileId(UUID cvProfileId);

    @Transactional
    @Modifying(flushAutomatically = true)
    @Query("delete from CvSkillMapping m where m.id.cvProfileId = :cvProfileId")
    int deleteByCvProfileId(@Param("cvProfileId") UUID cvProfileId);
}
