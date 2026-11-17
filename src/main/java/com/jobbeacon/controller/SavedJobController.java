package com.jobbeacon.controller;

import com.jobbeacon.dto.request.PageRequestDto;
import com.jobbeacon.dto.response.PageResponse;
import com.jobbeacon.dto.response.ResponseGeneral;
import com.jobbeacon.dto.response.SavedJobResponse;
import com.jobbeacon.security.GatewayHeaders;
import com.jobbeacon.service.SavedJobService;
import com.jobbeacon.service.UserService;
import com.jobbeacon.utils.JsonUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/me/saved-jobs")
@RequiredArgsConstructor
public class SavedJobController {

  private final SavedJobService savedJobService;
  private final UserService userService;

  @GetMapping
  public ResponseGeneral<PageResponse<SavedJobResponse>> getSavedJobs(
      @RequestHeader(GatewayHeaders.USER_ID) String uid,
      @RequestParam Map<String, Object> params
  ) {
    PageRequestDto pageRequest = JsonUtils.convert(params, PageRequestDto.class);
    return ResponseGeneral.ofSuccess("success", savedJobService.getSavedJobs(userService.getUserId(uid), pageRequest));
  }

  @GetMapping("/ids")
  public ResponseGeneral<List<UUID>> getSavedJobIds(@RequestHeader(GatewayHeaders.USER_ID) String uid) {
    return ResponseGeneral.ofSuccess("success", savedJobService.getSavedJobIds(userService.getUserId(uid)));
  }

  @PutMapping("/{jobId}")
  public ResponseGeneral<Void> saveJob(@RequestHeader(GatewayHeaders.USER_ID) String uid, @PathVariable("jobId") UUID jobId) {
    savedJobService.saveJob(userService.getUserId(uid), jobId);
    return ResponseGeneral.ofSuccess("success");
  }

  @DeleteMapping("/{jobId}")
  public ResponseGeneral<Void> unsaveJob(@RequestHeader(GatewayHeaders.USER_ID) String uid, @PathVariable("jobId") UUID jobId) {
    savedJobService.unsaveJob(userService.getUserId(uid), jobId);
    return ResponseGeneral.ofSuccess("success");
  }
}
