package com.jobbeacon.service;

import com.jobbeacon.dto.request.CompanySearchRequest;
import com.jobbeacon.dto.response.CompanySummaryResponse;
import com.jobbeacon.dto.response.PageResponse;
import com.jobbeacon.dto.response.ResponseGeneral;

public interface CompanyService {
    ResponseGeneral<PageResponse<CompanySummaryResponse>> searchCompanies(CompanySearchRequest companySearchRequest);
}
