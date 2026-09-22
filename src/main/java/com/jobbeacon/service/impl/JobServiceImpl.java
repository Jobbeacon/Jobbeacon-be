package com.jobbeacon.service.impl;

import com.jobbeacon.dto.message.JobPostingMessage;
import com.jobbeacon.dto.request.JobSearchRequest;
import com.jobbeacon.dto.response.JobSummaryResponse;
import com.jobbeacon.dto.response.PageResponse;
import com.jobbeacon.dto.response.ResponseGeneral;
import com.jobbeacon.entity.Category;
import com.jobbeacon.entity.Company;
import com.jobbeacon.entity.Job;
import com.jobbeacon.entity.JobSource;
import com.jobbeacon.entity.JobSourceMapping;
import com.jobbeacon.entity.Skill;
import com.jobbeacon.repository.CategoryRepository;
import com.jobbeacon.repository.CompanyRepository;
import com.jobbeacon.repository.JobRepository;
import com.jobbeacon.repository.JobSourceMappingRepository;
import com.jobbeacon.repository.JobSourceRepository;
import com.jobbeacon.repository.SkillRepository;
import com.jobbeacon.service.JobService;
import com.jobbeacon.service.base.BaseServiceImpl;
import com.jobbeacon.utils.PageUtils;
import com.jobbeacon.utils.UrlUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
public class JobServiceImpl extends BaseServiceImpl<Job> implements JobService {

    private final JobRepository jobRepository;
    private final CompanyRepository companyRepository;
    private final SkillRepository skillRepository;
    private final CategoryRepository categoryRepository;
    private final JobSourceRepository jobSourceRepository;
    private final JobSourceMappingRepository jobSourceMappingRepository;

    public JobServiceImpl(JobRepository jobRepository,
                          CompanyRepository companyRepository,
                          SkillRepository skillRepository,
                          CategoryRepository categoryRepository,
                          JobSourceRepository jobSourceRepository,
                          JobSourceMappingRepository jobSourceMappingRepository) {
        super(jobRepository);
        this.jobRepository = jobRepository;
        this.companyRepository = companyRepository;
        this.skillRepository = skillRepository;
        this.categoryRepository = categoryRepository;
        this.jobSourceRepository = jobSourceRepository;
        this.jobSourceMappingRepository = jobSourceMappingRepository;
    }

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
        if (message.getIndustry() != null && !message.getIndustry().trim().isEmpty()) {
            String[] industryArray = message.getIndustry().split(",");
            for (String categoryName : industryArray) {
                if (categoryName != null && !categoryName.trim().isEmpty()) {
                    String finalCategoryName = categoryName.trim();
                    Category category = categoryRepository.findByNameIgnoreCase(finalCategoryName)
                            .orElseGet(() -> {
                                Category newCategory = new Category();
                                newCategory.setName(finalCategoryName);
                                return categoryRepository.save(newCategory);
                            });
                    categories.add(category);
                }
            }
        }

        List<String> locations = new ArrayList<>();
        if (message.getLocation() != null && !message.getLocation().trim().isEmpty()) {
            String[] locationArray = message.getLocation().split("\\|");
            for (String locationName : locationArray) {
                if (locationName != null && !locationName.trim().isEmpty()) {
                    locations.add(locationName.trim());
                }
            }
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

        if (jobSource != null) {
            JobSourceMapping mapping = new JobSourceMapping();
            JobSourceMapping.JobSourceMappingId mappingId = new JobSourceMapping.JobSourceMappingId();
            mappingId.setJobId(job.getId());
            mappingId.setSourceId(jobSource.getId());

            mapping.setId(mappingId);
            mapping.setJob(job);
            mapping.setSource(jobSource);
            mapping.setJobUrl(message.getUrl());
            if (message.getCrawledAt() != null) {
                mapping.setCrawledAt(message.getCrawledAt().atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant());
            }

            jobSourceMappingRepository.save(mapping);
        }
    }

    @Override
    public ResponseGeneral<PageResponse<JobSummaryResponse>> searchJobs(JobSearchRequest req) {
        log.info("[searchJobs] - start search jobs with param: keyword = {}", req.getKeyword());
        Pageable pageable = PageUtils.buildPageable(req);
        Page<JobSummaryResponse> data = jobRepository.searchJobs(req.getKeyword(), pageable);
        List<JobSummaryResponse> jobs = new ArrayList<>(data.getContent());

        PageResponse<JobSummaryResponse> pageResponse = PageResponse.of(jobs, Math.toIntExact(data.getTotalElements()));
        return ResponseGeneral.ofSuccess( "success", pageResponse);
    }
}
