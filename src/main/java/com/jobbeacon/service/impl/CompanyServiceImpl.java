package com.jobbeacon.service.impl;

import com.jobbeacon.dto.request.CompanySearchRequest;
import com.jobbeacon.dto.response.CompanySummaryResponse;
import com.jobbeacon.dto.response.PageResponse;
import com.jobbeacon.dto.response.ResponseGeneral;
import com.jobbeacon.repository.CompanyRepository;
import com.jobbeacon.repository.JobLocationRepository;
import com.jobbeacon.repository.JobSourceMappingRepository;
import com.jobbeacon.repository.projection.CompanyJobCount;
import com.jobbeacon.repository.projection.CompanyProvince;
import com.jobbeacon.repository.projection.CompanySource;
import com.jobbeacon.service.CompanyService;
import com.jobbeacon.utils.PageUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CompanyServiceImpl implements CompanyService {

    private static final int MAX_LOCATIONS = 2;

    private final CompanyRepository companyRepository;
    private final JobLocationRepository jobLocationRepository;
    private final JobSourceMappingRepository jobSourceMappingRepository;

    @Override
    @Transactional(readOnly = true)
    public ResponseGeneral<PageResponse<CompanySummaryResponse>> searchCompanies(CompanySearchRequest req) {
        log.info("[searchCompanies] - start search companies with param: keyword = {}", req.getKeyword());

        req.setSortBy(null);
        Pageable pageable = PageUtils.buildPageable(req);
        Page<CompanyJobCount> data = companyRepository.searchCompanies(req.getKeyword(), pageable);

        List<UUID> companyIds = data.getContent().stream().map(CompanyJobCount::id).toList();
        Map<UUID, List<String>> locations = jobLocationRepository.getProvincesNameByCompanyIds(companyIds).stream().collect(Collectors.groupingBy(
                CompanyProvince::companyId,
                Collectors.mapping(CompanyProvince::value, Collectors.toList())
        ));
        Map<UUID, List<String>> sources = jobSourceMappingRepository.getSourcesByCompanyIds(companyIds).stream().collect(Collectors.groupingBy(
                CompanySource::companyId,
                Collectors.mapping(CompanySource::value, Collectors.toList())
        ));

        List<CompanySummaryResponse> companies = data.getContent().stream()
                .map(company -> new CompanySummaryResponse(
                        company.id(),
                        company.name(),
                        company.logoUrl(),
                        company.openJobsCount(),
                        locations.getOrDefault(company.id(), List.of()).stream().limit(MAX_LOCATIONS).toList(),
                        sources.getOrDefault(company.id(), List.of())
                ))
                .toList();

        PageResponse<CompanySummaryResponse> pageResponse = PageResponse.of(companies, Math.toIntExact(data.getTotalElements()));
        return ResponseGeneral.ofSuccess("success", pageResponse);
    }
}
