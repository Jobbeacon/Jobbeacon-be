package com.jobbeacon.repository;

import com.jobbeacon.entity.JobLshBand;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface JobLshBandRepository extends JpaRepository<JobLshBand, JobLshBand.JobLshBandId> {

    /**
     * Distinct ids of jobs of the given company sharing at least one (band_idx, band_hash) bucket.
     * {@code bandHashes} narrows the scan through the (band_idx, band_hash) index; {@code bandKeys} (encoded as
     * {@code "<band_idx>:<band_hash>"}) enforces the exact pair. Only jobs not expired at {@code now} (null expired_date included) are returned. When
     * {@code excludeSourceId} is not null, jobs that already have a mapping from that source are left out.
     */
    @Query(
            value = """
            select distinct b.job_id
            from job_lsh_band b
            join job j on j.id = b.job_id
            where b.band_hash in (:bandHashes)
              and (b.band_idx || ':' || b.band_hash) in (:bandKeys)
              and j.company_id = :companyId
              and (j.expired_date is null or j.expired_date > :now)
              and (cast(:excludeSourceId as uuid) is null or not exists (
                    select 1 from job_source_mapping m
                    where m.job_id = j.id and m.source_id = cast(:excludeSourceId as uuid)))
            """,
            nativeQuery = true
    )
    List<UUID> findCandidateJobIds(@Param("bandHashes") List<Long> bandHashes,
                                   @Param("bandKeys") List<String> bandKeys,
                                   @Param("companyId") UUID companyId,
                                   @Param("now") Instant now,
                                   @Param("excludeSourceId") UUID excludeSourceId);

    @Modifying
    @Query(value = "delete from job_lsh_band where job_id = :jobId", nativeQuery = true)
    void deleteByJobId(@Param("jobId") UUID jobId);
}
