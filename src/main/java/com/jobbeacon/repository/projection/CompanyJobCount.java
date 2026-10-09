package com.jobbeacon.repository.projection;

import java.util.UUID;

public record CompanyJobCount(UUID id, String name, String logoUrl, long openJobsCount) {
}
