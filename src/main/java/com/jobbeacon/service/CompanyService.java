package com.jobbeacon.service;

import com.jobbeacon.dto.request.CompanySearchRequest;
import com.jobbeacon.dto.request.PageRequestDto;
import com.jobbeacon.dto.response.CompanyDetailResponse;
import com.jobbeacon.dto.response.JobSummaryResponse;
import com.jobbeacon.dto.response.PageResponse;
import com.jobbeacon.dto.response.ResponseGeneral;

import java.util.UUID;

public interface CompanyService {
    ResponseGeneral<PageResponse<CompanyDetailResponse>> searchCompanies(CompanySearchRequest companySearchRequest);
    ResponseGeneral<CompanyDetailResponse> getCompanyById(UUID id);

    ResponseGeneral<PageResponse<JobSummaryResponse>> getJobsByCompanyId(UUID id, PageRequestDto pageRequestDto);
}
