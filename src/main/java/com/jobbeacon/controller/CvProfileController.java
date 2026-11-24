package com.jobbeacon.controller;

import com.jobbeacon.dto.request.AddCvSkillRequest;
import com.jobbeacon.dto.response.CvProfileResponse;
import com.jobbeacon.dto.response.CvProfileSkillsResponse;
import com.jobbeacon.dto.response.ResponseGeneral;
import com.jobbeacon.exception.CvExtractionException;
import com.jobbeacon.security.GatewayHeaders;
import com.jobbeacon.service.CvExtractionService;
import com.jobbeacon.service.CvProfileService;
import com.jobbeacon.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/me/cv-profile")
@RequiredArgsConstructor
public class CvProfileController {

  private final CvExtractionService cvExtractionService;
  private final CvProfileService cvProfileService;
  private final UserService userService;

  @PostMapping(value = "/extract", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseGeneral<CvProfileResponse> extract(
      @RequestHeader(GatewayHeaders.USER_ID) String uid,
      @RequestParam(value = "file", required = false) MultipartFile file
  ) {
    if (file == null || file.isEmpty()) {
      throw CvExtractionException.invalidFileType();
    }
    return ResponseGeneral.ofSuccess("success",
        CvProfileResponse.from(cvExtractionService.extract(userService.getUserId(uid), file)));
  }

  @GetMapping
  public ResponseGeneral<CvProfileResponse> getProfile(@RequestHeader(GatewayHeaders.USER_ID) String uid) {
    return ResponseGeneral.ofSuccess("success",
        CvProfileResponse.from(cvProfileService.getProfile(userService.getUserId(uid))));
  }

  @DeleteMapping
  public ResponseGeneral<Void> deleteProfile(@RequestHeader(GatewayHeaders.USER_ID) String uid) {
    cvProfileService.deleteProfile(userService.getUserId(uid));
    return ResponseGeneral.ofSuccess("success");
  }

  @GetMapping("/skills")
  public ResponseGeneral<CvProfileSkillsResponse> getSkills(@RequestHeader(GatewayHeaders.USER_ID) String uid) {
    return ResponseGeneral.ofSuccess("success",
        CvProfileSkillsResponse.from(cvProfileService.getSkills(userService.getUserId(uid))));
  }

  @PostMapping("/skills")
  public ResponseGeneral<CvProfileSkillsResponse> addSkill(
      @RequestHeader(GatewayHeaders.USER_ID) String uid,
      @Valid @RequestBody AddCvSkillRequest request
  ) {
    return ResponseGeneral.ofSuccess("success",
        CvProfileSkillsResponse.from(cvProfileService.addSkill(userService.getUserId(uid), request.getSkillId())));
  }

  @DeleteMapping("/skills/{skillId}")
  public ResponseGeneral<Void> deleteSkill(@RequestHeader(GatewayHeaders.USER_ID) String uid, @PathVariable UUID skillId) {
    cvProfileService.deleteSkill(userService.getUserId(uid), skillId);
    return ResponseGeneral.ofSuccess("success");
  }
}
