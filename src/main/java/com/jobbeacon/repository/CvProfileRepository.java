package com.jobbeacon.repository;

import com.jobbeacon.entity.CvProfile;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

public interface CvProfileRepository extends BaseRepository<CvProfile> {

    Optional<CvProfile> findByUserId(UUID userId);

    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from CvProfile p where p.userId = :userId")
    int deleteByUserId(@Param("userId") UUID userId);
}
