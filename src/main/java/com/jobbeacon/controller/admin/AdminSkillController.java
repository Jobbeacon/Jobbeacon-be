package com.jobbeacon.controller.admin;

import com.jobbeacon.dto.request.admin.AdminMergeRequest;
import com.jobbeacon.dto.request.admin.AdminPageQuery;
import com.jobbeacon.dto.request.admin.AdminSkillRequest;
import com.jobbeacon.dto.response.PageResponse;
import com.jobbeacon.dto.response.ResponseGeneral;
import com.jobbeacon.dto.response.admin.AdminSkillResponse;
import com.jobbeacon.dto.response.admin.AdminSkillStatsResponse;
import com.jobbeacon.service.admin.AdminSkillService;
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
@RequestMapping("/api/v1/admin/skills")
@RequiredArgsConstructor
public class AdminSkillController {

  private final AdminSkillService adminSkillService;

  @GetMapping("/stats")
  public ResponseGeneral<AdminSkillStatsResponse> stats() {
    return ResponseGeneral.ofSuccess("success", adminSkillService.stats());
  }

  @GetMapping
  public ResponseGeneral<PageResponse<AdminSkillResponse>> list(
      @RequestParam(required = false) String q,
      @RequestParam(required = false) String filter,
      @RequestParam(required = false) String sort,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer size
  ) {
    return ResponseGeneral.ofSuccess("success",
        adminSkillService.list(q, filter, sort, AdminPageQuery.of(page, size)));
  }

  @PostMapping
  public ResponseGeneral<AdminSkillResponse> create(@Valid @RequestBody AdminSkillRequest request) {
    return ResponseGeneral.ofCreated("success", adminSkillService.create(request));
  }

  @PutMapping("/{id}")
  public ResponseGeneral<AdminSkillResponse> update(
      @PathVariable UUID id,
      @Valid @RequestBody AdminSkillRequest request
  ) {
    return ResponseGeneral.ofSuccess("success", adminSkillService.update(id, request));
  }

  @PostMapping("/{id}/merge")
  public ResponseGeneral<AdminSkillResponse> merge(
      @PathVariable UUID id,
      @Valid @RequestBody AdminMergeRequest request
  ) {
    return ResponseGeneral.ofSuccess("success", adminSkillService.merge(id, request.getTargetId()));
  }

  @DeleteMapping("/{id}")
  public ResponseGeneral<Void> delete(@PathVariable UUID id) {
    adminSkillService.delete(id);
    return ResponseGeneral.ofSuccess("success");
  }
}
