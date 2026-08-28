package com.jobbeacon.controller;

import static com.jobbeacon.constant.JobBeaconConstants.CommonConstants.DEFAULT_LANGUAGE;
import static com.jobbeacon.constant.JobBeaconConstants.CommonConstants.LANGUAGE;
import static com.jobbeacon.constant.JobBeaconConstants.CommonConstants.SUCCESS;

import com.jobbeacon.dto.response.ResponseGeneral;
import com.jobbeacon.exception.base.NotFoundException;
import com.jobbeacon.service.base.MessageService;
import com.jobbeacon.utils.UuidUtils;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class HealthController {

  private final MessageService messageService;

  @GetMapping("/health")
  public ResponseGeneral<Map<String, UUID>> health(
      @RequestHeader(name = LANGUAGE, defaultValue = DEFAULT_LANGUAGE) String language
  ) {
    return ResponseGeneral.ofSuccess(
        messageService.getMessage(SUCCESS, language),
        Map.of("sampleId", UuidUtils.v7())
    );
  }

  @GetMapping("/demo/not-found")
  public ResponseGeneral<Void> demoNotFound(@RequestParam(defaultValue = "sample") String objectName) {
    throw new NotFoundException("com.jobbeacon.exception.base.NotFoundException.param", objectName);
  }
}
