package com.jobbeacon.controller;

import com.jobbeacon.dto.response.CvProfileResponse;
import com.jobbeacon.dto.response.ResponseGeneral;
import com.jobbeacon.entity.User;
import com.jobbeacon.exception.CvExtractionException;
import com.jobbeacon.security.CurrentUser;
import com.jobbeacon.service.CvExtractionService;
import com.jobbeacon.service.CvProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/me/cv-profile")
@RequiredArgsConstructor
public class CvProfileController {

  private final CvExtractionService cvExtractionService;
  private final CvProfileService cvProfileService;

  @PostMapping(value = "/extract", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseGeneral<CvProfileResponse> extract(
      @CurrentUser User user,
      @RequestParam(value = "file", required = false) MultipartFile file
  ) {
    if (file == null || file.isEmpty()) {
      throw CvExtractionException.invalidFileType();
    }
    return ResponseGeneral.ofSuccess("success",
        CvProfileResponse.from(cvExtractionService.extract(user.getId(), file)));
  }

  @GetMapping
  public ResponseGeneral<CvProfileResponse> getProfile(@CurrentUser User user) {
    return ResponseGeneral.ofSuccess("success",
        CvProfileResponse.from(cvProfileService.getProfile(user.getId())));
  }

  @DeleteMapping
  public ResponseGeneral<Void> deleteProfile(@CurrentUser User user) {
    cvProfileService.deleteProfile(user.getId());
    return ResponseGeneral.ofSuccess("success");
  }
}
