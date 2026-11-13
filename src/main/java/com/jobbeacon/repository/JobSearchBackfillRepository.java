package com.jobbeacon.repository;

import com.jobbeacon.entity.Job;
import com.jobbeacon.entity.JobLocation;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

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

    @Query(
        """
            select distinct j
            from Job j
            left join fetch j.company
            left join fetch j.skills
            left join fetch j.categories
            left join fetch j.jobSourceMappings m
            left join fetch m.source
            where j.id in :jobIds
        """
    )
    List<Job> findForSearchByIds(@Param("jobIds") Collection<UUID> jobIds);

    @Query(
        """
            select jl
            from JobLocation jl
            left join fetch jl.province
            where jl.job.id in :jobIds
        """
    )
    List<JobLocation> findLocationsForSearchByJobIds(@Param("jobIds") Collection<UUID> jobIds);
}
