package com.jobbeacon.repository;

import com.jobbeacon.entity.JobSource;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface JobSourceRepository extends BaseRepository<JobSource> {
    Optional<JobSource> findByDomainIgnoreCase(String domain);

    @Query("select distinct s.domain from JobSource s where s.domain is not null")
    List<String> findAllDomains();
}
