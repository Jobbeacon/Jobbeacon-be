package com.jobbeacon.repository;

import com.jobbeacon.entity.JobSourceMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface JobSourceMappingRepository extends JpaRepository<JobSourceMapping, JobSourceMapping.JobSourceMappingId> {
}
