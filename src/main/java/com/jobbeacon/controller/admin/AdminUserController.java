package com.jobbeacon.controller.admin;

import com.jobbeacon.constant.UserRole;
import com.jobbeacon.constant.UserStatus;
import com.jobbeacon.dto.request.admin.AdminPageQuery;
import com.jobbeacon.dto.request.admin.AdminUserUpdateRequest;
import com.jobbeacon.dto.response.PageResponse;
import com.jobbeacon.dto.response.ResponseGeneral;
import com.jobbeacon.dto.response.admin.AdminUserDetailResponse;
import com.jobbeacon.dto.response.admin.AdminUserRowResponse;
import com.jobbeacon.dto.response.admin.AdminUserStatsResponse;
import com.jobbeacon.service.admin.AdminUserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

  private final AdminUserService adminUserService;

  @GetMapping("/stats")
  public ResponseGeneral<AdminUserStatsResponse> stats() {
    return ResponseGeneral.ofSuccess("success", adminUserService.stats());
  }

  @GetMapping
  public ResponseGeneral<PageResponse<AdminUserRowResponse>> list(
      @RequestParam(required = false) String q,
      @RequestParam(required = false) UserRole role,
      @RequestParam(required = false) UserStatus status,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer size
  ) {
    return ResponseGeneral.ofSuccess("success",
        adminUserService.list(q, role, status, AdminPageQuery.of(page, size)));
  }

  @GetMapping("/{id}")
  public ResponseGeneral<AdminUserDetailResponse> detail(@PathVariable UUID id) {
    return ResponseGeneral.ofSuccess("success", adminUserService.detail(id));
  }

  @PutMapping("/{id}")
  public ResponseGeneral<AdminUserDetailResponse> update(
      @PathVariable UUID id,
      @Valid @RequestBody AdminUserUpdateRequest request
  ) {
    return ResponseGeneral.ofSuccess("success", adminUserService.update(id, request));
  }
}
