package com.jobbeacon.controller.admin;

import com.jobbeacon.dto.request.admin.AdminCompanyUpdateRequest;
import com.jobbeacon.dto.request.admin.AdminMergeRequest;
import com.jobbeacon.dto.request.admin.AdminPageQuery;
import com.jobbeacon.dto.response.PageResponse;
import com.jobbeacon.dto.response.ResponseGeneral;
import com.jobbeacon.dto.response.admin.AdminCompanyDetailResponse;
import com.jobbeacon.dto.response.admin.AdminCompanyRowResponse;
import com.jobbeacon.dto.response.admin.AdminCompanyStatsResponse;
import com.jobbeacon.service.admin.AdminCompanyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/companies")
@RequiredArgsConstructor
public class AdminCompanyController {

  private final AdminCompanyService adminCompanyService;

  @GetMapping("/stats")
  public ResponseGeneral<AdminCompanyStatsResponse> stats() {
    return ResponseGeneral.ofSuccess("success", adminCompanyService.stats());
  }

  @GetMapping
  public ResponseGeneral<PageResponse<AdminCompanyRowResponse>> list(
      @RequestParam(required = false) String q,
      @RequestParam(required = false) String sort,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer size
  ) {
    return ResponseGeneral.ofSuccess("success", adminCompanyService.list(q, sort, AdminPageQuery.of(page, size)));
  }

  @GetMapping("/{id}")
  public ResponseGeneral<AdminCompanyDetailResponse> detail(@PathVariable UUID id) {
    return ResponseGeneral.ofSuccess("success", adminCompanyService.detail(id));
  }

  @PutMapping("/{id}")
  public ResponseGeneral<AdminCompanyDetailResponse> update(
      @PathVariable UUID id,
      @Valid @RequestBody AdminCompanyUpdateRequest request
  ) {
    return ResponseGeneral.ofSuccess("success", adminCompanyService.update(id, request));
  }

  @PostMapping("/{id}/merge")
  public ResponseGeneral<AdminCompanyDetailResponse> merge(
      @PathVariable UUID id,
      @Valid @RequestBody AdminMergeRequest request
  ) {
    return ResponseGeneral.ofSuccess("success", adminCompanyService.merge(id, request.getTargetId()));
  }

  @DeleteMapping("/{id}")
  public ResponseGeneral<Void> delete(@PathVariable UUID id) {
    adminCompanyService.delete(id);
    return ResponseGeneral.ofSuccess("success");
  }
}
