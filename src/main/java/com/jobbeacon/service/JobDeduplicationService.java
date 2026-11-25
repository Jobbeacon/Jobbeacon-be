package com.jobbeacon.service;

import com.jobbeacon.entity.Job;
import com.jobbeacon.utils.dedup.JobTitleNormalizer;

import java.time.Instant;
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
     * Finds an existing job of the same company that is a cross-site duplicate of the given fingerprint.
     */
    Optional<Job> findDuplicate(UUID companyId, JobFingerprint fp, Instant postedDate, Long minSalary, Long maxSalary);

    /**
     * Replaces the LSH bands of a job.
     */
    void saveBands(UUID jobId, long[] bands);
}
