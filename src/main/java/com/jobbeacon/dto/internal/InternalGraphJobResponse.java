package com.jobbeacon.dto.internal;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** A job as the recommendation graph needs it. */
public record InternalGraphJobResponse(
        UUID id,
        String title,
        UUID companyId,
        String companyName,
        Instant postedDate,
        Instant expiredDate,
        List<Skill> skills,
        List<Source> sources
) {

    public record Skill(UUID id, String name, String normalizedName) {
    }

    public record Source(String domain, String jobUrl) {
    }
}
