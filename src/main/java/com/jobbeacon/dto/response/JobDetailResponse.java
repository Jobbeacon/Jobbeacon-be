package com.jobbeacon.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record JobDetailResponse (
    UUID id,
    CompanyResponse company,
    String title,
    String description,
    Integer experienceMonths,
    String employmentType,
    Instant postedDate,
    Instant expiredDate,
    Long minSalary,
    Long maxSalary,
    String currency,
    String salaryPeriod,
    List<String> location,
    String benefits,
    List<SkillResponse> skills,
    List<CategoryResponse> categories,
    List<JobSourceResponse> jobSources
){}
