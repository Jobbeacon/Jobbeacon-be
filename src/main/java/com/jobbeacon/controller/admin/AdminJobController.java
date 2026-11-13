package com.jobbeacon.controller.admin;

import com.jobbeacon.dto.request.admin.AdminJobUpdateRequest;
import com.jobbeacon.dto.request.admin.AdminPageQuery;
import com.jobbeacon.dto.response.PageResponse;
import com.jobbeacon.dto.response.ResponseGeneral;
import com.jobbeacon.dto.response.admin.AdminJobDetailResponse;
import com.jobbeacon.dto.response.admin.AdminJobRowResponse;
import com.jobbeacon.dto.response.admin.AdminJobStatsResponse;
import com.jobbeacon.repository.admin.AdminJobQueryRepository;
import com.jobbeacon.service.admin.AdminJobService;
import com.jobbeacon.utils.AdminTime;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/jobs")
@RequiredArgsConstructor
public class AdminJobController {

  private final AdminJobService adminJobService;

  @GetMapping("/stats")
  public ResponseGeneral<AdminJobStatsResponse> stats() {
    return ResponseGeneral.ofSuccess("success", adminJobService.stats());
  }

  @GetMapping
  public ResponseGeneral<PageResponse<AdminJobRowResponse>> list(
      @RequestParam(required = false) String q,
      @RequestParam(required = false) String source,
      @RequestParam(name = "employment_type", required = false) String employmentType,
      @RequestParam(name = "company_id", required = false) UUID companyId,
      @RequestParam(required = false) String crawled,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String sort,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer size
  ) {
    Instant crawledSince = switch (crawled == null ? "" : crawled.toLowerCase()) {
      case "today" -> AdminTime.startOfToday();
      case "month" -> AdminTime.startOfMonth();
      default -> null;
    };
    AdminJobQueryRepository.Filter filter = new AdminJobQueryRepository.Filter(
        q, source, employmentType, companyId, crawledSince, status, sort);
    return ResponseGeneral.ofSuccess("success", adminJobService.list(filter, AdminPageQuery.of(page, size)));
  }

  @GetMapping("/{id}")
  public ResponseGeneral<AdminJobDetailResponse> detail(@PathVariable UUID id) {
    return ResponseGeneral.ofSuccess("success", adminJobService.detail(id));
  }

  @PutMapping("/{id}")
  public ResponseGeneral<AdminJobDetailResponse> update(
      @PathVariable UUID id,
      @Valid @RequestBody AdminJobUpdateRequest request
  ) {
    return ResponseGeneral.ofSuccess("success", adminJobService.update(id, request));
  }

  @DeleteMapping("/{id}")
  public ResponseGeneral<Void> delete(@PathVariable UUID id) {
    adminJobService.delete(id);
    return ResponseGeneral.ofSuccess("success");
  }
}
