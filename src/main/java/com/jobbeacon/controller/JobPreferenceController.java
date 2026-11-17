package com.jobbeacon.controller;

import com.jobbeacon.dto.request.JobPreferenceRequest;
import com.jobbeacon.dto.response.JobPreferenceResponse;
import com.jobbeacon.dto.response.ResponseGeneral;
import com.jobbeacon.security.GatewayHeaders;
import com.jobbeacon.service.JobPreferenceService;
import com.jobbeacon.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me/job-preference")
@RequiredArgsConstructor
public class JobPreferenceController {

  private final JobPreferenceService jobPreferenceService;
  private final UserService userService;

  @GetMapping
  public ResponseGeneral<JobPreferenceResponse> getPreference(@RequestHeader(GatewayHeaders.USER_ID) String uid) {
    return ResponseGeneral.ofSuccess("success", jobPreferenceService.getPreference(userService.getUserId(uid)));
  }

  @PutMapping
  public ResponseGeneral<JobPreferenceResponse> updatePreference(
      @RequestHeader(GatewayHeaders.USER_ID) String uid,
      @Valid @RequestBody JobPreferenceRequest request
  ) {
    return ResponseGeneral.ofSuccess("success", jobPreferenceService.updatePreference(userService.getUserId(uid), request));
  }
}
