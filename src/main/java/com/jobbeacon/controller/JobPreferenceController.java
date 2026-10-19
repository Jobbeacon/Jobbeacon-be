package com.jobbeacon.controller;

import com.jobbeacon.dto.request.JobPreferenceRequest;
import com.jobbeacon.dto.response.JobPreferenceResponse;
import com.jobbeacon.dto.response.ResponseGeneral;
import com.jobbeacon.entity.User;
import com.jobbeacon.security.CurrentUser;
import com.jobbeacon.service.JobPreferenceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me/job-preference")
@RequiredArgsConstructor
public class JobPreferenceController {

  private final JobPreferenceService jobPreferenceService;

  @GetMapping
  public ResponseGeneral<JobPreferenceResponse> getPreference(@CurrentUser User user) {
    return ResponseGeneral.ofSuccess("success", jobPreferenceService.getPreference(user.getId()));
  }

  @PutMapping
  public ResponseGeneral<JobPreferenceResponse> updatePreference(
      @CurrentUser User user,
      @Valid @RequestBody JobPreferenceRequest request
  ) {
    return ResponseGeneral.ofSuccess("success", jobPreferenceService.updatePreference(user.getId(), request));
  }
}
