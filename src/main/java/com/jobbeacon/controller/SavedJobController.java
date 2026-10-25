package com.jobbeacon.controller;

import com.jobbeacon.dto.request.PageRequestDto;
import com.jobbeacon.dto.response.PageResponse;
import com.jobbeacon.dto.response.ResponseGeneral;
import com.jobbeacon.dto.response.SavedJobResponse;
import com.jobbeacon.entity.User;
import com.jobbeacon.security.CurrentUser;
import com.jobbeacon.service.SavedJobService;
import com.jobbeacon.utils.JsonUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
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

  @GetMapping
  public ResponseGeneral<PageResponse<SavedJobResponse>> getSavedJobs(
      @CurrentUser User user,
      @RequestParam Map<String, Object> params
  ) {
    PageRequestDto pageRequest = JsonUtils.convert(params, PageRequestDto.class);
    return ResponseGeneral.ofSuccess("success", savedJobService.getSavedJobs(user.getId(), pageRequest));
  }

  @GetMapping("/ids")
  public ResponseGeneral<List<UUID>> getSavedJobIds(@CurrentUser User user) {
    return ResponseGeneral.ofSuccess("success", savedJobService.getSavedJobIds(user.getId()));
  }

  @PutMapping("/{jobId}")
  public ResponseGeneral<Void> saveJob(@CurrentUser User user, @PathVariable("jobId") UUID jobId) {
    savedJobService.saveJob(user.getId(), jobId);
    return ResponseGeneral.ofSuccess("success");
  }

  @DeleteMapping("/{jobId}")
  public ResponseGeneral<Void> unsaveJob(@CurrentUser User user, @PathVariable("jobId") UUID jobId) {
    savedJobService.unsaveJob(user.getId(), jobId);
    return ResponseGeneral.ofSuccess("success");
  }
}
