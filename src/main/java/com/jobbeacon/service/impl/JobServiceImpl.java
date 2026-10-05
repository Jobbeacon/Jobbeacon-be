package com.jobbeacon.service.impl;

import com.jobbeacon.dto.message.JobPostingMessage;
import com.jobbeacon.dto.request.JobSearchRequest;
import com.jobbeacon.dto.response.CategoryResponse;
import com.jobbeacon.dto.response.CompanyResponse;
import com.jobbeacon.dto.response.JobDetailResponse;
import com.jobbeacon.dto.response.JobSourceResponse;
import com.jobbeacon.dto.response.JobSummaryResponse;
import com.jobbeacon.dto.response.SkillResponse;
import com.jobbeacon.dto.response.PageResponse;
import com.jobbeacon.dto.response.ResponseGeneral;
import com.jobbeacon.entity.Category;
import com.jobbeacon.entity.Company;
import com.jobbeacon.entity.Job;
import com.jobbeacon.entity.JobLocation;
import com.jobbeacon.entity.JobSource;
import com.jobbeacon.entity.JobSourceMapping;
import com.jobbeacon.entity.Skill;
import com.jobbeacon.exception.base.NotFoundException;
import com.jobbeacon.repository.CompanyRepository;
import com.jobbeacon.repository.JobLocationRepository;
import com.jobbeacon.repository.JobElasticsearchRepository;
import com.jobbeacon.repository.JobRepository;
import com.jobbeacon.repository.JobSourceRepository;
import com.jobbeacon.repository.SkillRepository;
import com.jobbeacon.search.JobSearchOutboxService;
import com.jobbeacon.service.CategoryClassificationService;
import com.jobbeacon.service.JobService;
import com.jobbeacon.service.LocationMappingService;
import com.jobbeacon.utils.UrlUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class JobServiceImpl implements JobService {

    private static final String JOB_NOT_FOUND_CODE = "job_not_found";

    private final JobRepository jobRepository;
    private final CompanyRepository companyRepository;
    private final SkillRepository skillRepository;
    private final CategoryClassificationService categoryClassificationService;
    private final JobSourceRepository jobSourceRepository;
    private final LocationMappingService locationMappingService;
    private final JobLocationRepository jobLocationRepository;
    private final JobSearchOutboxService jobSearchOutboxService;
    private final JobElasticsearchRepository jobElasticsearchRepository;

    @Override
    @Transactional
    public void processJobPostingMessage(JobPostingMessage message) {
        log.info("Processing job posting message: {}", message.getTitle());

        Company company = null;
        if (message.getCompanyName() != null && !message.getCompanyName().trim().isEmpty()) {
            company = companyRepository.findByNameIgnoreCase(message.getCompanyName().trim())
                    .orElseGet(() -> {
                        Company newCompany = new Company();
                        newCompany.setName(message.getCompanyName().trim());
                        newCompany.setLogoUrl(message.getCompanyLogoUrl());
                        return companyRepository.save(newCompany);
                    });
        }

        String domain = UrlUtils.extractDomain(message.getUrl());
        JobSource jobSource = null;
        if (domain != null) {
            jobSource = jobSourceRepository.findByDomainIgnoreCase(domain)
                    .orElseGet(() -> {
                        JobSource newSource = new JobSource();
                        newSource.setDomain(domain);
                        return jobSourceRepository.save(newSource);
                    });
        }

        Set<Skill> skills = new HashSet<>();
        if (message.getSkills() != null) {
            for (String skillName : message.getSkills()) {
                if (skillName != null && !skillName.trim().isEmpty()) {
                    String finalSkillName = skillName.trim();
                    Skill skill = skillRepository.findByNameIgnoreCase(finalSkillName)
                            .orElseGet(() -> {
                                Skill newSkill = new Skill();
                                newSkill.setName(finalSkillName);
                                return skillRepository.save(newSkill);
                            });
                    skills.add(skill);
                }
            }
        }

        Set<Category> categories = new HashSet<>();
        categoryClassificationService.classifyDeepestCategory(message).ifPresent(categories::add);

        List<String> rawLocations = new ArrayList<>();
        if (message.getLocation() != null && !message.getLocation().trim().isEmpty()) {
            String[] locationArray = message.getLocation().split("\\|");
            for (String locationName : locationArray) {
                if (locationName != null && !locationName.trim().isEmpty()) {
                    rawLocations.add(locationName.trim());
                }
            }
        }
        List<LocationMappingService.MappedLocation> mappedLocations = new ArrayList<>();
        List<String> locations = new ArrayList<>();
        for (String rawLocation : rawLocations) {
            LocationMappingService.MappedLocation mapped = locationMappingService.map(rawLocation);
            mappedLocations.add(mapped);
            locations.add(mapped.display());
        }

        Job job = new Job();
        job.setCompany(company);
        job.setTitle(message.getTitle());
        job.setDescription(message.getDescription());
        job.setExperienceMonths(message.getExperienceMonths());
        job.setEmploymentType(message.getEmploymentType());

        if (message.getPostedDate() != null) {
            job.setPostedDate(message.getPostedDate().atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant());
        }
        if (message.getExpiredDate() != null) {
            job.setExpiredDate(message.getExpiredDate().atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant());
        }

        job.setMinSalary(message.getMinSalary());
        job.setMaxSalary(message.getMaxSalary());
        job.setCurrency(message.getCurrency());
        job.setSalaryPeriod(message.getSalaryPeriod());
        job.setLocation(locations);
        job.setBenefits(message.getBenefits());

        job.setSkills(skills);
        job.setCategories(categories);

        job = jobRepository.save(job);
        List<JobLocation> jobLocations = new ArrayList<>();
        for (int i = 0; i < mappedLocations.size(); i++) {
            LocationMappingService.MappedLocation mapped = mappedLocations.get(i);
            JobLocation jobLocation = new JobLocation();
            jobLocation.setJob(job);
            jobLocation.setRawLocation(rawLocations.get(i));
            jobLocation.setProvince(mapped.province());
            jobLocation.setWard(mapped.ward());
            jobLocations.add(jobLocationRepository.save(jobLocation));
        }

        if (jobSource != null) {
            JobSourceMapping mapping = new JobSourceMapping();
            mapping.setJob(job);
            mapping.setSource(jobSource);
            mapping.setJobUrl(message.getUrl());
            if (message.getCrawledAt() != null) {
                mapping.setCrawledAt(message.getCrawledAt().atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant());
            }
            // Cascade from Job persists this row. Saving it here as well calls merge()
            // because the embedded id is already non-null, then the collection flush
            // persists a second instance with the same id.
            job.getJobSourceMappings().add(mapping);
        }
        jobSearchOutboxService.created(job, jobLocations);
    }

    @Override
    public ResponseGeneral<PageResponse<JobSummaryResponse>> searchJobs(JobSearchRequest req) {
        log.info("[searchJobs] - start search jobs with param: keyword = {}", req.getKeyword());
        return ResponseGeneral.ofSuccess("success", jobElasticsearchRepository.search(req));
    }

    @Override
    @Transactional(readOnly = true)
    public ResponseGeneral<JobDetailResponse> getJobDetail(UUID jobId) {
        log.info("[getJobDetail] - start get job detail with id = {}", jobId);
        Job job = jobRepository.findDetailById(jobId)
                .orElseThrow(() -> new NotFoundException(JOB_NOT_FOUND_CODE));
        return ResponseGeneral.ofSuccess("success", toJobDetailResponse(job));
    }

    private JobDetailResponse toJobDetailResponse(Job job) {
        Company company = job.getCompany();
        CompanyResponse companyResponse = company == null
                ? null
                : new CompanyResponse(company.getId(), company.getName(), company.getLogoUrl());

        List<SkillResponse> skills = job.getSkills().stream()
                .sorted(Comparator.comparing(Skill::getName, String.CASE_INSENSITIVE_ORDER))
                .map(skill -> new SkillResponse(skill.getId(), skill.getName()))
                .toList();
        List<CategoryResponse> categories = job.getCategories().stream()
                .sorted(Comparator.comparing(Category::getName, String.CASE_INSENSITIVE_ORDER))
                .map(this::toCategoryTree)
                .toList();
        List<JobSourceResponse> jobSources = job.getJobSourceMappings().stream()
                .sorted(Comparator.comparing(
                        JobSourceMapping::getCrawledAt,
                        Comparator.nullsLast(Comparator.naturalOrder())
                ))
                .map(mapping -> new JobSourceResponse(
                        mapping.getSource().getId(),
                        mapping.getSource().getDomain(),
                        mapping.getJobUrl()
                ))
                .toList();

        return new JobDetailResponse(
                job.getId(),
                companyResponse,
                job.getTitle(),
                job.getDescription(),
                job.getExperienceMonths(),
                job.getEmploymentType(),
                job.getPostedDate(),
                job.getExpiredDate(),
                job.getMinSalary(),
                job.getMaxSalary(),
                job.getCurrency(),
                job.getSalaryPeriod(),
                job.getLocation(),
                job.getBenefits(),
                skills,
                categories,
                jobSources
        );
    }

    private CategoryResponse toCategoryTree(Category category) {
        CategoryResponse node = new CategoryResponse(category.getId(), category.getName(), category.getLevel(), List.of());
        for (Category parent = category.getParent(); parent != null; parent = parent.getParent()) {
            node = new CategoryResponse(parent.getId(), parent.getName(), parent.getLevel(), List.of(node));
        }
        return node;
    }
}
