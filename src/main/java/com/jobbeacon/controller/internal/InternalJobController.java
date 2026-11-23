package com.jobbeacon.controller.internal;

import com.jobbeacon.dto.internal.InternalIdsRequest;
import com.jobbeacon.dto.response.JobSummaryResponse;
import com.jobbeacon.repository.JobRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Job data for other services. Like every {@code /internal/**} path, the gateway does not route here. */
@RestController
@RequestMapping("/internal/v1/jobs")
@RequiredArgsConstructor
public class InternalJobController {

  private final JobRepository jobRepository;

  /**
   * Cards for the given jobs, as the job list shows them. Used by the recommendation service, which ranks job ids
   * and needs what to display. Missing and expired jobs are left out, and the order is unspecified.
   */
  @PostMapping("/summaries")
  @Transactional(readOnly = true)
  public List<JobSummaryResponse> findSummaries(@Valid @RequestBody InternalIdsRequest request) {
    if (request.ids().isEmpty()) {
      return List.of();
    }
    return jobRepository.findActiveSummariesByIds(request.ids());
  }
}
