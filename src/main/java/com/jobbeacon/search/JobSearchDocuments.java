package com.jobbeacon.search;

import com.jobbeacon.entity.Company;
import com.jobbeacon.entity.Job;
import com.jobbeacon.entity.JobLocation;
import com.jobbeacon.entity.JobSourceMapping;
import com.jobbeacon.entity.Province;
import com.jobbeacon.utils.HtmlTextUtils;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class JobSearchDocuments {

    private JobSearchDocuments() {
    }

    public static JobSearchDocument from(Job job, List<JobLocation> jobLocations) {
        Company company = job.getCompany();
        Map<String, String> provinces = new LinkedHashMap<>();
        if (jobLocations != null) {
            for (JobLocation jobLocation : jobLocations) {
                Province province = jobLocation.getProvince();
                if (province != null && province.getId() != null) {
                    provinces.putIfAbsent(province.getId(), province.getNameLocal());
                }
            }
        }

        List<JobSearchDocument.Ref> skills = sortedRefs(job.getSkills() == null ? List.of() : job.getSkills().stream()
                .map(skill -> new JobSearchDocument.Ref(skill.getId(), skill.getName()))
                .toList());
        List<JobSearchDocument.Ref> categories = sortedRefs(job.getCategories() == null ? List.of() : job.getCategories().stream()
                .map(category -> new JobSearchDocument.Ref(category.getId(), category.getName()))
                .toList());
        List<JobSearchDocument.SourceRef> sources = job.getJobSourceMappings() == null
                ? List.of()
                : job.getJobSourceMappings().stream()
                .map(JobSearchDocuments::sourceRef)
                .sorted(Comparator.comparing(JobSearchDocument.SourceRef::domain, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .toList();

        return new JobSearchDocument(
                job.getId(),
                job.getTitle(),
                HtmlTextUtils.toPlainText(job.getDescription()),
                HtmlTextUtils.toPlainText(job.getBenefits()),
                company == null ? null : company.getId(),
                company == null ? null : company.getName(),
                company == null ? null : company.getLogoUrl(),
                skills,
                categories,
                job.getLocation() == null ? List.of() : List.copyOf(job.getLocation()),
                List.copyOf(provinces.keySet()),
                provinces.values().stream().toList(),
                job.getEmploymentType(),
                job.getExperienceMonths(),
                job.getMinSalary(),
                job.getMaxSalary(),
                job.getCurrency(),
                job.getSalaryPeriod(),
                job.getPostedDate(),
                job.getExpiredDate(),
                sources
        );
    }

    private static List<JobSearchDocument.Ref> sortedRefs(List<JobSearchDocument.Ref> refs) {
        return refs.stream()
                .sorted(Comparator.comparing(JobSearchDocument.Ref::name, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .toList();
    }

    private static JobSearchDocument.SourceRef sourceRef(JobSourceMapping mapping) {
        String domain = mapping.getSource() == null ? null : mapping.getSource().getDomain();
        return new JobSearchDocument.SourceRef(domain, mapping.getJobUrl());
    }
}
