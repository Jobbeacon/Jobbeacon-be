package com.jobbeacon.controller;

import com.jobbeacon.dto.request.CompanySearchRequest;
import com.jobbeacon.dto.request.JobSearchRequest;
import com.jobbeacon.dto.request.PageRequestDto;
import com.jobbeacon.dto.response.ResponseGeneral;
import com.jobbeacon.service.CompanyService;
import com.jobbeacon.utils.JsonUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/companies")
@RequiredArgsConstructor
public class CompanyController {

    private final CompanyService companyService;

    @GetMapping
    public ResponseGeneral<?> searchCompanies(@RequestParam Map<String, Object> params) {
        CompanySearchRequest companySearchRequest = JsonUtils.convert(params, CompanySearchRequest.class);
        return companyService.searchCompanies(companySearchRequest);
    }
    
    @GetMapping("/{id}")
    public ResponseGeneral<?> getCompanyById(@PathVariable UUID id) {
        return companyService.getCompanyById(id);
    }
    
    @GetMapping("/{id}/jobs")
    public ResponseGeneral<?> getCompanyJobs(
            @PathVariable UUID id,
            @RequestParam Map<String, Object> params) {
        PageRequestDto pageRequestDto = JsonUtils.convert(params, PageRequestDto.class);
        return companyService.getJobsByCompanyId(id, pageRequestDto);
    }
}
