package com.jobbeacon.repository;

import com.jobbeacon.entity.JobSource;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface JobSourceRepository extends BaseRepository<JobSource> {
    Optional<JobSource> findByDomainIgnoreCase(String domain);
}
