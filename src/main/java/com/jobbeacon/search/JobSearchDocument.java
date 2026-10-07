package com.jobbeacon.search;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record JobSearchDocument(
        UUID id,
        String title,
        String description,
        String benefits,
        UUID companyId,
        String companyName,
        String companyLogoUrl,
        List<Ref> skills,
        List<Ref> categories,
        List<String> locations,
        List<String> provinceIds,
        List<String> provinceNames,
        String employmentType,
        Integer experienceMonths,
        Long minSalary,
        Long maxSalary,
        String currency,
        String salaryPeriod,
        Instant postedDate,
        Instant expiredDate,
        List<SourceRef> sources
) {
    public record Ref(UUID id, String name) {
    }

    public record SourceRef(String domain, String jobUrl) {
    }
}
