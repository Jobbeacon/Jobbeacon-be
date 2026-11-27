package com.jobbeacon.dto.internal;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** How many jobs to find for each source; 0 skips it. */
public record InternalContentJobsRequest(
        @NotNull UUID userId,
        @Min(0) @Max(200) int titleLimit,
        @Min(0) @Max(200) int semanticLimit
) {
}
