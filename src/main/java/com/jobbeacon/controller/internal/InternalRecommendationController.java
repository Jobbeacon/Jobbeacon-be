package com.jobbeacon.controller.internal;

import com.jobbeacon.dto.internal.InternalContentJobsRequest;
import com.jobbeacon.dto.internal.InternalContentJobsResponse;
import com.jobbeacon.dto.internal.InternalRecommendationContextRequest;
import com.jobbeacon.dto.internal.InternalRecommendationContextResponse;
import com.jobbeacon.internal.InternalRecommendationService;
import com.jobbeacon.internal.SemanticJobService;
import com.jobbeacon.internal.TitleJobService;
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
  private final TitleJobService titleJobService;
  private final SemanticJobService semanticJobService;

  /** The user's saved jobs, and how the given jobs fit their job preference and CV. */
  @PostMapping("/context")
  public InternalRecommendationContextResponse context(
      @Valid @RequestBody InternalRecommendationContextRequest request
  ) {
    return internalRecommendationService.context(request.userId(), request.jobIds());
  }

  /** Open jobs found from the titles the user looks for and from what their CV is about, for the ranking to fuse. */
  @PostMapping("/content-jobs")
  public InternalContentJobsResponse contentJobs(@Valid @RequestBody InternalContentJobsRequest request) {
    return new InternalContentJobsResponse(
        titleJobService.findJobIds(request.userId(), request.titleLimit()),
        semanticJobService.findJobIds(request.userId(), request.semanticLimit()));
  }
}
