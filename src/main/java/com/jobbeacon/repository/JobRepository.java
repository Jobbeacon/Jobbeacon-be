package com.jobbeacon.repository;

import com.jobbeacon.dto.response.JobSummaryResponse;
import com.jobbeacon.entity.Job;
import com.jobbeacon.repository.projection.JobPreferenceFacts;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface JobRepository extends BaseRepository<Job> {

    @Query(
        """
            select new com.jobbeacon.dto.response.JobSummaryResponse(
                j.id, j.title, c.name, j.minSalary, j.maxSalary, j.location, j.currency, c.logoUrl, j.postedDate
            )
            from Job j
            left join j.company c
            where :keyword is null or :keyword = ''  or LOWER(j.title) LIKE LOWER(CONCAT('%', :keyword, '%'))
            order by j.postedDate desc
        """
    )
    Page<JobSummaryResponse> searchJobs(@Param("keyword") String keyword, Pageable pageable);

    @Query(
        """
            select distinct j
            from Job j
            left join fetch j.company
            left join fetch j.skills
            left join fetch j.categories
            left join fetch j.jobSourceMappings m
            left join fetch m.source
            where j.id = :jobId
        """
    )
    Optional<Job> findDetailById(@Param("jobId") UUID jobId);
    
    @Query(
        """
            select new com.jobbeacon.dto.response.JobSummaryResponse(
                j.id, j.title, c.name, j.minSalary, j.maxSalary, j.location, j.currency, c.logoUrl, j.postedDate
            )
            from Job j
            left join j.company c
            where c.id = :companyId and (j.expiredDate is null or j.expiredDate > CURRENT_TIMESTAMP)
            order by j.postedDate desc
        """
    )
    Page<JobSummaryResponse> findJobsByCompanyId(@Param("companyId") UUID companyId, Pageable pageable);

    List<Job> findByCompanyIdAndContentHash(UUID companyId, String contentHash);

    List<Job> findByIdIn(Collection<UUID> ids);

    @Modifying(flushAutomatically = true)
    @Query("update Job j set j.company.id = :targetId where j.company.id = :sourceId")
    int moveJobsToCompany(@Param("sourceId") UUID sourceId, @Param("targetId") UUID targetId);

    boolean existsByCompanyId(UUID companyId);

    /** Job cards for the recommendation service. Expired jobs are left out; the order is unspecified. */
    @Query(
        """
            select new com.jobbeacon.dto.response.JobSummaryResponse(
                j.id, j.title, c.name, j.minSalary, j.maxSalary, j.location, j.currency, c.logoUrl, j.postedDate
            )
            from Job j
            left join j.company c
            where j.id in :ids and (j.expiredDate is null or j.expiredDate > CURRENT_TIMESTAMP)
        """
    )
    List<JobSummaryResponse> findActiveSummariesByIds(@Param("ids") Collection<UUID> ids);

    @Query(
        """
            select new com.jobbeacon.repository.projection.JobPreferenceFacts(
                j.id, j.employmentType, j.minSalary, j.maxSalary, j.currency, j.salaryPeriod, j.experienceMonths
            )
            from Job j
            where j.id in :ids
        """
    )
    List<JobPreferenceFacts> findPreferenceFactsByIds(@Param("ids") Collection<UUID> ids);
}
