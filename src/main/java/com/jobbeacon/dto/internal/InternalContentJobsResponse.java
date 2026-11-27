package com.jobbeacon.dto.internal;

import java.util.List;
import java.util.UUID;

/**
 * Open jobs found from what the user looks for, each list best first.
 *
 * @param titleJobIds    with the words of a title they look for (or, if they stated none, one from their CV)
 * @param semanticJobIds closest in meaning to their desired titles and CV; empty until those have been embedded
 */
public record InternalContentJobsResponse(List<UUID> titleJobIds, List<UUID> semanticJobIds) {
}
