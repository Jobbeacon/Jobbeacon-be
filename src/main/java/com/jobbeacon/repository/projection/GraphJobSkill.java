package com.jobbeacon.repository.projection;

import java.util.UUID;

public record GraphJobSkill(UUID jobId, UUID skillId, String name, String normalizedName) {
}
