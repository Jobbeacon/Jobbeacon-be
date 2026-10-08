package com.jobbeacon.controller;

import com.jobbeacon.dto.request.CompanySearchRequest;
import com.jobbeacon.dto.response.ResponseGeneral;
import com.jobbeacon.service.CompanyService;
import com.jobbeacon.utils.JsonUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

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
}
