package com.jobbeacon.service.admin.impl;

import com.jobbeacon.constant.EmploymentType;
import com.jobbeacon.dto.request.admin.AdminJobUpdateRequest;
import com.jobbeacon.dto.request.admin.AdminPageQuery;
import com.jobbeacon.dto.response.PageResponse;
import com.jobbeacon.dto.response.admin.AdminJobDetailResponse;
import com.jobbeacon.dto.response.admin.AdminJobDetailResponse.CategoryRef;
import com.jobbeacon.dto.response.admin.AdminJobDetailResponse.Mapping;
import com.jobbeacon.dto.response.admin.AdminJobDetailResponse.NamedRef;
import com.jobbeacon.dto.response.admin.AdminJobRowResponse;
import com.jobbeacon.dto.response.admin.AdminJobStatsResponse;
import com.jobbeacon.entity.Category;
import com.jobbeacon.entity.Company;
import com.jobbeacon.entity.Job;
import com.jobbeacon.entity.JobLocation;
import com.jobbeacon.entity.JobSourceMapping;
import com.jobbeacon.entity.Skill;
import com.jobbeacon.exception.base.BadRequestException;
import com.jobbeacon.exception.base.NotFoundException;
import com.jobbeacon.repository.CategoryRepository;
import com.jobbeacon.repository.CompanyRepository;
import com.jobbeacon.repository.JobLocationRepository;
import com.jobbeacon.repository.JobRepository;
import com.jobbeacon.repository.SkillRepository;
import com.jobbeacon.repository.admin.AdminJobQueryRepository;
import com.jobbeacon.search.JobSearchOutboxService;
import com.jobbeacon.repository.SemanticEmbeddingRepository;
import com.jobbeacon.search.JobSearchReindexer;
import com.jobbeacon.service.LocationMappingService;
import com.jobbeacon.service.admin.AdminJobService;
import com.jobbeacon.utils.AdminTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminJobServiceImpl implements AdminJobService {

  private static final String JOB_NOT_FOUND = "job_not_found";

  private final AdminJobQueryRepository queryRepository;
  private final JobRepository jobRepository;
  private final CompanyRepository companyRepository;
  private final CategoryRepository categoryRepository;
  private final SkillRepository skillRepository;
  private final JobLocationRepository jobLocationRepository;
  private final LocationMappingService locationMappingService;
  private final JobSearchOutboxService jobSearchOutboxService;
  private final JobSearchReindexer jobSearchReindexer;
  private final SemanticEmbeddingRepository semanticEmbeddingRepository;

  @Override
  @Transactional(readOnly = true)
  public AdminJobStatsResponse stats() {
    return queryRepository.stats(AdminTime.startOfToday());
  }

  @Override
  @Transactional(readOnly = true)
  public PageResponse<AdminJobRowResponse> list(AdminJobQueryRepository.Filter filter, AdminPageQuery page) {
    long total = queryRepository.count(filter);
    List<AdminJobRowResponse> rows = total == 0 ? List.of() : queryRepository.find(filter, page);
    return PageResponse.of(rows, (int) Math.min(total, Integer.MAX_VALUE));
  }

  @Override
  @Transactional(readOnly = true)
  public AdminJobDetailResponse detail(UUID id) {
    Job job = jobRepository.findDetailById(id).orElseThrow(() -> new NotFoundException(JOB_NOT_FOUND));
    return toDetail(job);
  }

  @Override
  @Transactional
  public AdminJobDetailResponse update(UUID id, AdminJobUpdateRequest request) {
    log.info("(admin update job) id={}", id);
    Job job = jobRepository.findDetailById(id).orElseThrow(() -> new NotFoundException(JOB_NOT_FOUND));

    if (request.getMinSalary() != null && request.getMaxSalary() != null
        && request.getMaxSalary() < request.getMinSalary()) {
      throw new BadRequestException("admin_job_invalid_salary_range");
    }
    String employmentType = blankToNull(request.getEmploymentType());
    if (employmentType != null && !employmentType.equals(job.getEmploymentType())
        && Arrays.stream(EmploymentType.values()).noneMatch(t -> t.name().equals(employmentType))) {
      throw new BadRequestException("admin_job_invalid_employment_type");
    }
    Company company = companyRepository.findById(request.getCompanyId())
        .orElseThrow(() -> new NotFoundException("company_not_found"));

    Set<Category> categories = new HashSet<>(loadAll(request.getCategoryIds(), categoryRepository::findAllById,
        "category_not_found"));
    Set<Skill> skills = new HashSet<>(loadAll(request.getSkillIds(), skillRepository::findAllById,
        "skill_not_found"));

    job.setTitle(request.getTitle().trim());
    job.setCompany(company);
    job.setEmploymentType(employmentType);
    job.setExperienceMonths(request.getExperienceMonths());
    job.setMinSalary(request.getMinSalary());
    job.setMaxSalary(request.getMaxSalary());
    job.setCurrency(blankToNull(request.getCurrency()));
    job.setSalaryPeriod(blankToNull(request.getSalaryPeriod()));
    job.setPostedDate(request.getPostedDate());
    job.setExpiredDate(request.getExpiredDate());
    job.setDescription(request.getDescription());
    job.setBenefits(request.getBenefits());
    job.getCategories().clear();
    job.getCategories().addAll(categories);
    job.getSkills().clear();
    job.getSkills().addAll(skills);

    List<String> locations = cleanLocations(request.getLocations());
    List<String> current = job.getLocation() == null ? List.of() : job.getLocation();
    if (request.getLocations() != null && !locations.equals(current)) {
      replaceLocations(job, locations);
    }
    jobRepository.save(job);
    semanticEmbeddingRepository.markJobChanged(id);
    jobSearchReindexer.reindex(List.of(id));
    return detail(id);
  }

  @Override
  @Transactional
  public void delete(UUID id) {
    log.info("(admin delete job) id={}", id);
    Job job = jobRepository.findById(id).orElseThrow(() -> new NotFoundException(JOB_NOT_FOUND));
    jobLocationRepository.deleteByJobId(id);
    job.getSkills().clear();
    job.getCategories().clear();
    job.getJobSourceMappings().clear();
    jobRepository.delete(job);
    jobSearchOutboxService.deleted(id);
  }

  private void replaceLocations(Job job, List<String> rawLocations) {
    jobLocationRepository.deleteByJobId(job.getId());
    List<String> display = new ArrayList<>();
    for (String raw : rawLocations) {
      LocationMappingService.MappedLocation mapped = locationMappingService.map(raw);
      display.add(mapped.display());
      JobLocation location = new JobLocation();
      location.setJob(job);
      location.setRawLocation(raw);
      location.setProvince(mapped.province());
      location.setWard(mapped.ward());
      jobLocationRepository.save(location);
    }
    job.setLocation(display);
  }

  private static List<String> cleanLocations(List<String> locations) {
    if (locations == null) {
      return List.of();
    }
    return new ArrayList<>(locations.stream()
        .filter(Objects::nonNull)
        .map(String::trim)
        .filter(value -> !value.isEmpty())
        .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new)));
  }

  private static <T> List<T> loadAll(List<UUID> ids, java.util.function.Function<Iterable<UUID>, List<T>> finder,
                                     String notFoundCode) {
    if (ids == null || ids.isEmpty()) {
      return List.of();
    }
    Set<UUID> unique = new LinkedHashSet<>(ids);
    unique.remove(null);
    List<T> found = finder.apply(unique);
    if (found.size() != unique.size()) {
      throw new NotFoundException(notFoundCode);
    }
    return found;
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  private AdminJobDetailResponse toDetail(Job job) {
    Company company = job.getCompany();
    List<JobSourceMapping> mappings = job.getJobSourceMappings().stream()
        .sorted(Comparator.comparing(JobSourceMapping::getCrawledAt, Comparator.nullsLast(Comparator.reverseOrder())))
        .toList();
    List<String> sources = mappings.stream()
        .map(mapping -> mapping.getSource() == null ? null : mapping.getSource().getDomain())
        .filter(Objects::nonNull)
        .distinct()
        .sorted(String.CASE_INSENSITIVE_ORDER)
        .toList();
    return new AdminJobDetailResponse(
        job.getId(),
        job.getTitle(),
        company == null ? null : company.getId(),
        company == null ? null : company.getName(),
        job.getLocation() == null ? List.of() : List.copyOf(job.getLocation()),
        job.getMinSalary(),
        job.getMaxSalary(),
        job.getCurrency(),
        job.getSalaryPeriod(),
        job.getEmploymentType(),
        job.getPostedDate(),
        job.getExpiredDate(),
        sources,
        mappings.isEmpty() ? null : mappings.get(0).getCrawledAt(),
        job.getDescription(),
        job.getBenefits(),
        job.getExperienceMonths(),
        job.getSkills().stream()
            .sorted(Comparator.comparing(Skill::getName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
            .map(skill -> new NamedRef(skill.getId(), skill.getName()))
            .toList(),
        job.getCategories().stream()
            .map(category -> new CategoryRef(category.getId(), category.getName(), pathOf(category)))
            .sorted(Comparator.comparing(ref -> String.join(" > ", ref.path())))
            .toList(),
        mappings.stream()
            .map(mapping -> new Mapping(
                mapping.getSource() == null ? null : mapping.getSource().getDomain(),
                mapping.getJobUrl(),
                mapping.getCrawledAt()))
            .toList()
    );
  }

  static List<String> pathOf(Category category) {
    List<String> path = new ArrayList<>();
    for (Category node = category; node != null && path.size() < 10; node = node.getParent()) {
      path.add(0, node.getName());
    }
    return path;
  }
}
