package com.jobbeacon.controller.admin;

import com.jobbeacon.dto.request.admin.AdminCategoryRequest;
import com.jobbeacon.dto.response.ResponseGeneral;
import com.jobbeacon.dto.response.admin.AdminCategoryNodeResponse;
import com.jobbeacon.service.admin.AdminCategoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/categories")
@RequiredArgsConstructor
public class AdminCategoryController {

  private final AdminCategoryService adminCategoryService;

  @GetMapping
  public ResponseGeneral<List<AdminCategoryNodeResponse>> tree() {
    return ResponseGeneral.ofSuccess("success", adminCategoryService.tree());
  }

  @PostMapping
  public ResponseGeneral<AdminCategoryNodeResponse> create(@Valid @RequestBody AdminCategoryRequest request) {
    return ResponseGeneral.ofCreated("success", adminCategoryService.create(request));
  }

  @PutMapping("/{id}")
  public ResponseGeneral<AdminCategoryNodeResponse> rename(
      @PathVariable UUID id,
      @Valid @RequestBody AdminCategoryRequest request
  ) {
    return ResponseGeneral.ofSuccess("success", adminCategoryService.rename(id, request));
  }

  @DeleteMapping("/{id}")
  public ResponseGeneral<Void> delete(@PathVariable UUID id) {
    adminCategoryService.delete(id);
    return ResponseGeneral.ofSuccess("success");
  }
}
