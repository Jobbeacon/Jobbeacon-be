package com.jobbeacon.repository;

import com.jobbeacon.entity.Job;
import com.jobbeacon.entity.JobLocation;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface JobSearchBackfillRepository extends BaseRepository<Job> {

    @Query(
        """
            select distinct j
            from Job j
            left join fetch j.company
            left join fetch j.skills
            left join fetch j.categories
            left join fetch j.jobSourceMappings m
            left join fetch m.source
        """
    )
    List<Job> findAllForSearchBackfill();

    @Query(
        """
            select jl
            from JobLocation jl
            left join fetch jl.province
            left join fetch jl.job
        """
    )
    List<JobLocation> findLocationsForSearchBackfill();
}
