package com.jobbeacon.service.admin.impl;

import com.jobbeacon.dto.request.admin.AdminCompanyUpdateRequest;
import com.jobbeacon.dto.request.admin.AdminPageQuery;
import com.jobbeacon.dto.response.PageResponse;
import com.jobbeacon.dto.response.admin.AdminCompanyDetailResponse;
import com.jobbeacon.dto.response.admin.AdminCompanyDetailResponse.Duplicate;
import com.jobbeacon.dto.response.admin.AdminCompanyRowResponse;
import com.jobbeacon.dto.response.admin.AdminCompanyStatsResponse;
import com.jobbeacon.entity.Company;
import com.jobbeacon.exception.base.BadRequestException;
import com.jobbeacon.exception.base.ConflictException;
import com.jobbeacon.exception.base.NotFoundException;
import com.jobbeacon.repository.CompanyRepository;
import com.jobbeacon.repository.JobRepository;
import com.jobbeacon.repository.admin.AdminCompanyQueryRepository;
import com.jobbeacon.repository.admin.AdminCompanyQueryRepository.CompanyName;
import com.jobbeacon.search.JobSearchReindexer;
import com.jobbeacon.service.admin.AdminCompanyService;
import com.jobbeacon.utils.CompanyNameUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminCompanyServiceImpl implements AdminCompanyService {

  private static final String COMPANY_NOT_FOUND = "company_not_found";
  private static final int MAX_DUPLICATES = 10;

  private final AdminCompanyQueryRepository queryRepository;
  private final CompanyRepository companyRepository;
  private final JobRepository jobRepository;
  private final JobSearchReindexer jobSearchReindexer;

  @Override
  @Transactional(readOnly = true)
  public AdminCompanyStatsResponse stats() {
    return queryRepository.stats();
  }

  @Override
  @Transactional(readOnly = true)
  public PageResponse<AdminCompanyRowResponse> list(String q, String sort, AdminPageQuery page) {
    long total = queryRepository.count(q);
    List<AdminCompanyRowResponse> rows = total == 0 ? List.of() : queryRepository.find(q, sort, page);
    return PageResponse.of(rows, (int) Math.min(total, Integer.MAX_VALUE));
  }

  @Override
  @Transactional(readOnly = true)
  public AdminCompanyDetailResponse detail(UUID id) {
    AdminCompanyRowResponse row = queryRepository.findRow(id);
    if (row == null) {
      throw new NotFoundException(COMPANY_NOT_FOUND);
    }
    return new AdminCompanyDetailResponse(
        row.id(), row.name(), row.logoUrl(), row.jobCount(), row.openJobCount(), row.sources(),
        row.createdAt(), row.lastCrawledAt(), queryRepository.provinces(id), duplicatesOf(row.id(), row.name()));
  }

  private List<Duplicate> duplicatesOf(UUID id, String name) {
    String key = CompanyNameUtils.key(name);
    List<CompanyName> twins = queryRepository.allNames().stream()
        .filter(other -> !other.id().equals(id))
        .filter(other -> CompanyNameUtils.likelySame(key, CompanyNameUtils.key(other.name())))
        .limit(MAX_DUPLICATES)
        .toList();
    Map<UUID, Long> counts = queryRepository.jobCounts(twins.stream().map(CompanyName::id).toList());
    return twins.stream()
        .map(twin -> new Duplicate(twin.id(), twin.name(), counts.getOrDefault(twin.id(), 0L)))
        .sorted(Comparator.comparingLong(Duplicate::jobCount).reversed()
            .thenComparing(Duplicate::name, String.CASE_INSENSITIVE_ORDER))
        .toList();
  }

  @Override
  @Transactional
  public AdminCompanyDetailResponse update(UUID id, AdminCompanyUpdateRequest request) {
    log.info("(admin update company) id={}", id);
    Company company = companyRepository.findById(id).orElseThrow(() -> new NotFoundException(COMPANY_NOT_FOUND));
    String name = request.getName().trim();
    companyRepository.findByNameIgnoreCase(name)
        .filter(other -> !other.getId().equals(id))
        .ifPresent(other -> {
          throw new ConflictException("admin_company_name_exists");
        });
    String logo = request.getLogoUrl() == null || request.getLogoUrl().isBlank() ? null : request.getLogoUrl().trim();
    boolean changed = !name.equals(company.getName()) || !Objects.equals(logo, company.getLogoUrl());
    company.setName(name);
    company.setLogoUrl(logo);
    companyRepository.save(company);
    if (changed) {
      jobSearchReindexer.reindex(queryRepository.jobIds(id));
    }
    return detail(id);
  }

  @Override
  @Transactional
  public AdminCompanyDetailResponse merge(UUID sourceId, UUID targetId) {
    log.info("(admin merge company) source={} target={}", sourceId, targetId);
    if (sourceId.equals(targetId)) {
      throw new BadRequestException("admin_merge_same_target");
    }
    Company source = companyRepository.findById(sourceId).orElseThrow(() -> new NotFoundException(COMPANY_NOT_FOUND));
    Company target = companyRepository.findById(targetId).orElseThrow(() -> new NotFoundException(COMPANY_NOT_FOUND));
    boolean logoTaken = (target.getLogoUrl() == null || target.getLogoUrl().isBlank())
        && source.getLogoUrl() != null && !source.getLogoUrl().isBlank();
    if (logoTaken) {
      target.setLogoUrl(source.getLogoUrl());
      companyRepository.save(target);
    }
    List<UUID> moved = queryRepository.jobIds(sourceId);
    jobRepository.moveJobsToCompany(sourceId, targetId);
    companyRepository.delete(source);
    // a logo taken over from the source changes every target job's search document
    jobSearchReindexer.reindex(logoTaken ? queryRepository.jobIds(targetId) : moved);
    return detail(targetId);
  }

  @Override
  @Transactional
  public void delete(UUID id) {
    log.info("(admin delete company) id={}", id);
    Company company = companyRepository.findById(id).orElseThrow(() -> new NotFoundException(COMPANY_NOT_FOUND));
    if (jobRepository.existsByCompanyId(id)) {
      throw new ConflictException("admin_company_has_jobs");
    }
    companyRepository.delete(company);
  }
}
