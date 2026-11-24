package com.jobbeacon.dto.internal;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record InternalTitleJobsRequest(
        @NotNull UUID userId,
        @Min(1) @Max(200) int limit
) {
}
