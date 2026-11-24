package com.jobbeacon.repository.projection;

import java.time.Instant;
import java.util.UUID;

public record GraphJob(
        UUID id,
        String title,
        UUID companyId,
        String companyName,
        Instant postedDate,
        Instant expiredDate
) {
}
