package com.jobbeacon.controller.internal;

import com.jobbeacon.dto.internal.InternalRecommendationContextRequest;
import com.jobbeacon.dto.internal.InternalRecommendationContextResponse;
import com.jobbeacon.dto.internal.InternalTitleJobsRequest;
import com.jobbeacon.internal.InternalRecommendationService;
import com.jobbeacon.internal.TitleJobService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/internal/v1/recommendation")
@RequiredArgsConstructor
public class InternalRecommendationController {

  private final InternalRecommendationService internalRecommendationService;
  private final TitleJobService titleJobService;

  /** The user's saved jobs, and how the given jobs fit their job preference. */
  @PostMapping("/context")
  public InternalRecommendationContextResponse context(
      @Valid @RequestBody InternalRecommendationContextRequest request
  ) {
    return internalRecommendationService.context(request.userId(), request.jobIds());
  }

  /** Open jobs with a title the user looks for (or, if they stated none, one from their CV), best match first. */
  @PostMapping("/title-jobs")
  public List<UUID> titleJobs(@Valid @RequestBody InternalTitleJobsRequest request) {
    return titleJobService.findJobIds(request.userId(), request.limit());
  }
}
