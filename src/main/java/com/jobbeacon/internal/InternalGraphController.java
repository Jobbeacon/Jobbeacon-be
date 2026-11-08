package com.jobbeacon.internal;

import com.jobbeacon.dto.internal.InternalGraphCandidatesResponse;
import com.jobbeacon.dto.internal.InternalGraphJobResponse;
import com.jobbeacon.dto.internal.InternalIdPageResponse;
import com.jobbeacon.dto.internal.InternalIdsRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Source data for the recommendation service's graph. Internal: callers must send the shared token (see
 * {@link InternalTokenFilter}). Responses are plain bodies, not wrapped in {@code ResponseGeneral}.
 */
@Validated
@RestController
@RequestMapping("/internal/v1/graph")
@RequiredArgsConstructor
public class InternalGraphController {

  private final InternalGraphService internalGraphService;

  /** Jobs that no longer exist are left out. */
  @PostMapping("/jobs")
  public List<InternalGraphJobResponse> findJobs(@Valid @RequestBody InternalIdsRequest request) {
    return internalGraphService.findJobs(request.ids());
  }

  @PostMapping("/candidates")
  public InternalGraphCandidatesResponse findCandidates(@Valid @RequestBody InternalIdsRequest request) {
    return internalGraphService.findCandidates(request.ids());
  }

  /** Job ids in ascending order after {@code after}, for the recommendation backfill. */
  @GetMapping("/job-ids")
  public InternalIdPageResponse findJobIds(
      @RequestParam(required = false) UUID after,
      @RequestParam @Min(1) @Max(5000) int limit
  ) {
    return new InternalIdPageResponse(internalGraphService.findJobIds(after, limit));
  }

  /** Ids of users with a CV or saved jobs, in ascending order after {@code after}. */
  @GetMapping("/candidate-ids")
  public InternalIdPageResponse findCandidateIds(
      @RequestParam(required = false) UUID after,
      @RequestParam @Min(1) @Max(5000) int limit
  ) {
    return new InternalIdPageResponse(internalGraphService.findCandidateIds(after, limit));
  }
}
