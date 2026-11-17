package com.jobbeacon.repository.projection;

import java.util.UUID;

public record GraphJobSource(UUID jobId, String domain, String jobUrl) {
}
