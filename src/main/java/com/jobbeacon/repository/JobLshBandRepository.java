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
     * {@code "<band_idx>:<band_hash>"}) enforces the exact pair. Jobs with null posted_date are kept.
     */
    @Query(
            value = """
            select distinct b.job_id
            from job_lsh_band b
            join job j on j.id = b.job_id
            where b.band_hash in (:bandHashes)
              and (b.band_idx || ':' || b.band_hash) in (:bandKeys)
              and j.company_id = :companyId
              and (j.posted_date is null or j.posted_date between :fromDate and :toDate)
            """,
            nativeQuery = true
    )
    List<UUID> findCandidateJobIds(@Param("bandHashes") List<Long> bandHashes,
                                   @Param("bandKeys") List<String> bandKeys,
                                   @Param("companyId") UUID companyId,
                                   @Param("fromDate") Instant fromDate,
                                   @Param("toDate") Instant toDate);

    @Modifying
    @Query(value = "delete from job_lsh_band where job_id = :jobId", nativeQuery = true)
    void deleteByJobId(@Param("jobId") UUID jobId);
}
