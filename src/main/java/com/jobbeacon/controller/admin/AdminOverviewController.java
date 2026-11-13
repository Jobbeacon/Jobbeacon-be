package com.jobbeacon.controller.admin;

import com.jobbeacon.dto.response.ResponseGeneral;
import com.jobbeacon.dto.response.admin.AdminOverviewResponse;
import com.jobbeacon.service.admin.AdminOverviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/overview")
@RequiredArgsConstructor
public class AdminOverviewController {

  private final AdminOverviewService adminOverviewService;

  @GetMapping
  public ResponseGeneral<AdminOverviewResponse> overview(@RequestParam(defaultValue = "30") int days) {
    return ResponseGeneral.ofSuccess("success", adminOverviewService.overview(days));
  }
}
