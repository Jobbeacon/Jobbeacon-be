package com.jobbeacon.dto.response;

import java.util.List;
import java.util.UUID;

public record CompanyDetailResponse(
    UUID id,
    String name,
    String logoUrl,
    Long openJobsCount,
    List<String> locations,
    List<String> sources
) {}
