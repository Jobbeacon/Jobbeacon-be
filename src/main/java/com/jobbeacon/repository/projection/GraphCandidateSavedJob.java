package com.jobbeacon.repository.projection;

import java.time.Instant;
import java.util.UUID;

public record GraphCandidateSavedJob(
        UUID userId,
        UUID jobId,
        Instant savedAt
) {
}
