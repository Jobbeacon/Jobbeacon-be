package com.jobbeacon.controller.internal;

import com.jobbeacon.dto.internal.InternalRecommendationContextRequest;
import com.jobbeacon.dto.internal.InternalRecommendationContextResponse;
import com.jobbeacon.internal.InternalRecommendationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/recommendation")
@RequiredArgsConstructor
public class InternalRecommendationController {

  private final InternalRecommendationService internalRecommendationService;

  /** The user's saved jobs, and how the given jobs fit their job preference. */
  @PostMapping("/context")
  public InternalRecommendationContextResponse context(
      @Valid @RequestBody InternalRecommendationContextRequest request
  ) {
    return internalRecommendationService.context(request.userId(), request.jobIds());
  }
}
