package com.jobbeacon.dto.response.admin;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AdminSkillResponse(
    UUID id,
    String name,
    List<String> aliases,
    long jobCount,
    long cvCount,
    Instant createdAt
) {
}
