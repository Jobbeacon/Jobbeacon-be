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
import com.jobbeacon.repository.CategoryRepository;
import com.jobbeacon.repository.CompanyRepository;
import com.jobbeacon.repository.JobLocationRepository;
import com.jobbeacon.repository.JobElasticsearchRepository;
import com.jobbeacon.graph.GraphSyncOutboxService;
import com.jobbeacon.repository.JobRepository;
import com.jobbeacon.repository.JobSourceMappingRepository;
import com.jobbeacon.repository.JobSourceRepository;
import com.jobbeacon.search.JobSearchOutboxService;
import com.jobbeacon.repository.SemanticEmbeddingRepository;
import com.jobbeacon.service.CategoryClassificationService;
import com.jobbeacon.service.JobDeduplicationService;
import com.jobbeacon.service.JobService;
import com.jobbeacon.service.LocationMappingService;
import com.jobbeacon.service.SkillResolutionService;
import com.jobbeacon.utils.SourceJobKeys;
import com.jobbeacon.utils.UrlUtils;
import com.jobbeacon.utils.dedup.CompanyNameNormalizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

@Slf4j
@Service
@RequiredArgsConstructor
public class JobServiceImpl implements JobService {

    private static final String JOB_NOT_FOUND_CODE = "job_not_found";
    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    /** Postgres rounds timestamps to microseconds, so a redelivered message can look slightly newer than its row. */
    private static final Duration CRAWLED_AT_PRECISION = Duration.ofMillis(1);

    private final JobRepository jobRepository;
    private final CompanyRepository companyRepository;
    private final SkillResolutionService skillResolutionService;
    private final CategoryClassificationService categoryClassificationService;
    private final JobSourceRepository jobSourceRepository;
    private final JobSourceMappingRepository jobSourceMappingRepository;
    private final LocationMappingService locationMappingService;
    private final JobLocationRepository jobLocationRepository;
    private final JobSearchOutboxService jobSearchOutboxService;
    private final GraphSyncOutboxService graphSyncOutboxService;
    private final SemanticEmbeddingRepository semanticEmbeddingRepository;
    private final JobElasticsearchRepository jobElasticsearchRepository;
    private final CategoryRepository categoryRepository;
    private final JobDeduplicationService jobDeduplicationService;

    @Override
    @Transactional
    public void processJobPostingMessage(JobPostingMessage message) {
        log.info("Processing job posting message: {}", message.getTitle());

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

        // The same posting arrives more than once: crawled again, found under another url, or redelivered by RabbitMQ.
        String sourceJobKey = SourceJobKeys.from(message.getUrl());
        JobSourceMapping known = jobSource == null || sourceJobKey == null
                ? null
                : jobSourceMappingRepository.findBySourceJobKey(jobSource.getId(), sourceJobKey).orElse(null);
        Instant crawledAt = toInstant(message.getCrawledAt());
        if (known != null && !isNewer(crawledAt, known.getCrawledAt())) {
            log.info("Skip job posting already processed: {}", message.getUrl());
            return;
        }

        Company company = resolveCompany(message);

        Set<Skill> skills = skillResolutionService.resolveSkills(message.getSkills());

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

        boolean isNew = known == null;
        Job job = isNew ? new Job() : known.getJob();
        applyPosting(job, message, isNew);
        // As with the fields, an update that found none of these keeps what the job has.
        if (isNew || company != null) {
            job.setCompany(company);
        }
        if (isNew || !locations.isEmpty()) {
            job.setLocation(locations);
        }
        if (isNew || !skills.isEmpty()) {
            job.setSkills(skills);
        }
        if (isNew || !categories.isEmpty()) {
            job.setCategories(categories);
        }

        // From the job rather than the message: on an update a missing description keeps the stored one.
        JobDeduplicationService.JobFingerprint fp =
                jobDeduplicationService.fingerprint(job.getTitle(), job.getDescription());
        job.setTitleNormalized(fp.titleNormalized());
        job.setContentHash(fp.contentHash());
        job.setMinhash(fp.minhash());

        job = jobRepository.save(job);
        jobDeduplicationService.saveBands(job.getId(), fp.bands());
        List<JobLocation> jobLocations;
        if (!isNew && mappedLocations.isEmpty()) {
            jobLocations = jobLocationRepository.findByJobId(job.getId());
        } else {
            if (!isNew) {
                jobLocationRepository.deleteByJobId(job.getId());
            }
            jobLocations = new ArrayList<>();
            for (int i = 0; i < mappedLocations.size(); i++) {
                LocationMappingService.MappedLocation mapped = mappedLocations.get(i);
                JobLocation jobLocation = new JobLocation();
                jobLocation.setJob(job);
                jobLocation.setRawLocation(rawLocations.get(i));
                jobLocation.setProvince(mapped.province());
                jobLocation.setWard(mapped.ward());
                jobLocations.add(jobLocationRepository.save(jobLocation));
            }
        }

        if (!isNew) {
            known.setJobUrl(message.getUrl());
            known.setCrawledAt(crawledAt);
            jobSearchOutboxService.updated(job, jobLocations);
        } else {
            if (jobSource != null) {
                JobSourceMapping mapping = new JobSourceMapping();
                mapping.setJob(job);
                mapping.setSource(jobSource);
                mapping.setJobUrl(message.getUrl());
                mapping.setSourceJobKey(sourceJobKey);
                mapping.setCrawledAt(crawledAt);
                // Cascade from Job persists this row. Saving it here as well calls merge()
                // because the embedded id is already non-null, then the collection flush
                // persists a second instance with the same id.
                job.getJobSourceMappings().add(mapping);
            }
            jobSearchOutboxService.created(job, jobLocations);
        }
        graphSyncOutboxService.jobChanged(job.getId());
        semanticEmbeddingRepository.markJobChanged(job.getId());
    }

    @Override
    @Transactional
    public boolean tryMergeDuplicate(JobPostingMessage message) {
        JobSource jobSource = resolveJobSource(message);
        String sourceJobKey = SourceJobKeys.from(message.getUrl());
        // A posting already known on its own site, or without a key, is left to processJobPostingMessage.
        if (jobSource == null || sourceJobKey == null
                || (jobSourceMappingRepository.findBySourceJobKey(jobSource.getId(), sourceJobKey)
                .isPresent())) {
            return false;
        }
        Company company = resolveCompany(message);
        if (company == null) {
            return false;
        }
        JobDeduplicationService.JobFingerprint fp =
                jobDeduplicationService.fingerprint(message.getTitle(), message.getDescription());
        Optional<Job> duplicate = jobDeduplicationService.findDuplicate(
                company.getId(), jobSource == null ? null : jobSource.getId(), fp,
                message.getMinSalary(), message.getMaxSalary());
        if (duplicate.isEmpty()) {
            return false;
        }
        Job job = duplicate.get();

        // Only the source list of the existing job changes; its columns and fingerprint stay as they are.
        JobSourceMapping mapping = new JobSourceMapping();
        mapping.setJob(job);
        mapping.setSource(jobSource);
        mapping.setJobUrl(message.getUrl());
        mapping.setSourceJobKey(sourceJobKey);
        mapping.setCrawledAt(toInstant(message.getCrawledAt()));
        // Persisted by cascade from Job on flush, see processJobPostingMessage.
        job.getJobSourceMappings().add(mapping);
        jobSearchOutboxService.updated(job, jobLocationRepository.findByJobId(job.getId()));
        graphSyncOutboxService.jobChanged(job.getId());
        log.info("(tryMergeDuplicate) merged into jobId: {}, source: {}, url: {}",
                job.getId(), jobSource.getDomain(), message.getUrl());
        return true;
    }

    private Company resolveCompany(JobPostingMessage message) {
        if (message.getCompanyName() == null || message.getCompanyName().trim().isEmpty()) {
            return null;
        }
        String name = message.getCompanyName().trim();
        String normalized = CompanyNameNormalizer.normalize(name);
        Optional<Company> found = normalized == null || normalized.isEmpty()
                ? Optional.empty()
                : companyRepository.findFirstByNormalizedNameOrderByIdAsc(normalized);
        if (found.isPresent()) {
            return found.get();
        }
        Optional<Company> byName = companyRepository.findByNameIgnoreCase(name);
        if (byName.isPresent()) {
            Company company = byName.get();
            if (company.getNormalizedName() == null) {
                company.setNormalizedName(normalized);
                company = companyRepository.save(company);
            }
            return company;
        }
        Company newCompany = new Company();
        newCompany.setName(name);
        newCompany.setLogoUrl(message.getCompanyLogoUrl());
        newCompany.setNormalizedName(normalized);
        return companyRepository.save(newCompany);
    }

    private JobSource resolveJobSource(JobPostingMessage message) {
        String domain = UrlUtils.extractDomain(message.getUrl());
        if (domain == null) {
            return null;
        }
        return jobSourceRepository.findByDomainIgnoreCase(domain)
                .orElseGet(() -> {
                    JobSource newSource = new JobSource();
                    newSource.setDomain(domain);
                    return jobSourceRepository.save(newSource);
                });
    }

    /**
     * Copies the posting onto the job. On an update a value the crawler did not find keeps the old one: a page it could
     * only partly read must not wipe what an earlier crawl got.
     */
    private static void applyPosting(Job job, JobPostingMessage message, boolean isNew) {
        setIfFound(message.getTitle(), job::setTitle);
        setIfFound(message.getDescription(), job::setDescription);
        setIfFound(message.getEmploymentType(), job::setEmploymentType);
        setIfFound(toInstant(message.getPostedDate()), job::setPostedDate);
        setIfFound(toInstant(message.getExpiredDate()), job::setExpiredDate);
        setIfFound(message.getMinSalary(), job::setMinSalary);
        setIfFound(message.getMaxSalary(), job::setMaxSalary);
        setIfFound(message.getCurrency(), job::setCurrency);
        setIfFound(message.getSalaryPeriod(), job::setSalaryPeriod);
        setIfFound(message.getBenefits(), job::setBenefits);
        // 0 is what the crawler sends when it found no experience requirement.
        if (isNew || message.getExperienceMonths() > 0) {
            job.setExperienceMonths(message.getExperienceMonths());
        }
    }

    private static <T> void setIfFound(T value, Consumer<T> setter) {
        if (value != null && !(value instanceof String text && text.isBlank())) {
            setter.accept(value);
        }
    }

    private static Instant toInstant(LocalDateTime value) {
        return value == null ? null : value.atZone(VIETNAM_ZONE).toInstant();
    }

    /** Without both times there is nothing to compare, so a message without one only ever adds a job. */
    private static boolean isNewer(Instant crawledAt, Instant storedAt) {
        return crawledAt != null && (storedAt == null || crawledAt.isAfter(storedAt.plus(CRAWLED_AT_PRECISION)));
    }

    @Override
    public ResponseGeneral<PageResponse<JobSummaryResponse>> searchJobs(JobSearchRequest req) {
        log.info(
                "[searchJobs] keyword={} location={} locations={} categories={} skills={} salary={}-{} experience={} employment={} postedWithin={} sources={} sort={}",
                req.getKeyword(),
                req.getLocation(),
                req.getLocations(),
                req.getCategories(),
                req.getSkills(),
                req.getMinSalary(),
                req.getMaxSalary(),
                req.getExperience(),
                req.getEmploymentTypes(),
                req.getPostedWithin(),
                req.getSources(),
                req.getSortBy()
        );
        req.setCategories(expandCategoryFilter(req.getCategories()));
        return ResponseGeneral.ofSuccess("success", jobElasticsearchRepository.search(req));
    }

    /**
     * A chosen category also matches jobs tagged with any descendant. The stored
     * category is often the deepest node, while the filter can name a parent.
     */
    private String expandCategoryFilter(String raw) {
        if (raw == null || raw.isBlank()) {
            return raw;
        }
        List<String> selected = Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(name -> !name.isEmpty())
                .toList();
        if (selected.isEmpty()) {
            return raw;
        }
        Set<String> names = new LinkedHashSet<>(selected);
        for (Object[] row : categoryRepository.findAllForTree()) {
            String name = (String) row[1];
            String path = (String) row[4];
            if (name == null || path == null) {
                continue;
            }
            for (String chosen : selected) {
                if (path.equals(chosen) || path.startsWith(chosen + " > ")) {
                    names.add(name);
                    break;
                }
            }
        }
        return String.join(",", names);
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
