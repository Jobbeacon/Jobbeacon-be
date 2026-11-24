package com.jobbeacon.dto.internal;

import com.jobbeacon.repository.projection.GraphCandidateSavedJob;
import com.jobbeacon.repository.projection.GraphCandidateSkill;

import java.util.List;
import java.util.UUID;

/**
 * What the backend knows about a set of users. {@code existingUserIds} is separate because a user with no skills and
 * no saved jobs still exists, and must not be removed from the graph.
 */
public record InternalGraphCandidatesResponse(
        List<UUID> existingUserIds,
        List<GraphCandidateSkill> skills,
        List<GraphCandidateSavedJob> savedJobs
) {
}
