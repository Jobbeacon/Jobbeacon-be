package com.jobbeacon.service;

import com.jobbeacon.entity.Job;
import com.jobbeacon.utils.dedup.JobTitleNormalizer;

import java.util.Optional;
import java.util.UUID;

public interface JobDeduplicationService {

    /**
     * Dedup fingerprint of a job. When the description has no text, {@code contentHash}, {@code minhash} and
     * {@code descriptionNormalized} are null and {@code bands} is empty.
     */
    record JobFingerprint(String titleNormalized,
                          JobTitleNormalizer.Seniority seniority,
                          String contentHash,
                          int[] minhash,
                          long[] bands,
                          String descriptionNormalized) {
    }

    JobFingerprint fingerprint(String title, String description);

    /**
     * Finds an existing job of the same company that is a cross-site duplicate of the given fingerprint. When
     * {@code sourceId} is not null, jobs that already have a mapping from that source are never candidates: on one site
     * a different posting key means a separate opening. Only jobs not yet expired are candidates; the posted date
     * is not compared.
     */
    Optional<Job> findDuplicate(UUID companyId, UUID sourceId, JobFingerprint fp, Long minSalary,
                                Long maxSalary);

    /**
     * Replaces the LSH bands of a job.
     */
    void saveBands(UUID jobId, long[] bands);
}
