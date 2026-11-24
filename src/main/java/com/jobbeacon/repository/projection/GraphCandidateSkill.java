package com.jobbeacon.repository.projection;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record GraphCandidateSkill(
        UUID userId,
        UUID skillId,
        String name,
        String normalizedName,
        BigDecimal confidence,
        Integer experienceMonths,
        LocalDate lastUsedAt
) {
}
