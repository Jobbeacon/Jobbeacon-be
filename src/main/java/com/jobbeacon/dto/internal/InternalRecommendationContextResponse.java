package com.jobbeacon.dto.internal;

import java.util.List;
import java.util.UUID;

/** What the backend knows about a user that should change which jobs they are recommended. */
public record InternalRecommendationContextResponse(
        List<UUID> savedJobIds,
        List<PreferenceMatch> matches
) {

    /**
     * How a job fits the user's job preference. Null means it cannot be told: the user stated no such preference, or the
     * job does not say. That is common (most postings hide the salary), so it must not count as a mismatch.
     */
    public record PreferenceMatch(
            UUID jobId,
            Boolean title,
            Boolean location,
            Boolean employmentType,
            Boolean salary
    ) {
    }
}
