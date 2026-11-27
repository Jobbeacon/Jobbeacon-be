package com.jobbeacon.dto.internal;

import java.util.List;
import java.util.UUID;

/** What the backend knows about a user that should change which jobs they are recommended. */
public record InternalRecommendationContextResponse(
        List<UUID> savedJobIds,
        List<PreferenceMatch> matches
) {

    /**
     * How a job fits the user's job preference and CV. Titles are not here: jobs are found by title separately
     * (see {@code TitleJobService}). Null means it cannot be told: the user stated no such preference, or the
     * job does not say. That is common (most postings hide the salary), so it must not count as a mismatch.
     *
     * @param missingExperienceMonths how much experience the job asks for beyond what the CV shows, 0 when it is enough
     */
    public record PreferenceMatch(
            UUID jobId,
            Boolean location,
            Boolean employmentType,
            Boolean salary,
            Integer missingExperienceMonths
    ) {
    }
}
