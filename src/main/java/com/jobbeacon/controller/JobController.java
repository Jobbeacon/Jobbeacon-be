package com.jobbeacon.controller;

import com.jobbeacon.dto.request.JobSearchRequest;
import com.jobbeacon.dto.response.JobFacetsResponse;
import com.jobbeacon.dto.response.ResponseGeneral;
import com.jobbeacon.service.JobFacetService;
import com.jobbeacon.service.JobService;
import com.jobbeacon.utils.JsonUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/jobs")
@RequiredArgsConstructor
public class JobController {

    private final JobService jobService;
    private final JobFacetService jobFacetService;

    @GetMapping
    public ResponseGeneral<?> searchJobs(@RequestParam Map<String, Object> params){
        JobSearchRequest jobSearchRequest = JsonUtils.convert(params, JobSearchRequest.class);
        return jobService.searchJobs(jobSearchRequest);
    }

    @GetMapping("/facets")
    public ResponseGeneral<JobFacetsResponse> facets() {
        return ResponseGeneral.ofSuccess(
                "success",
                new JobFacetsResponse(jobFacetService.rootCategories(), jobFacetService.skillsInUse())
        );
    }

    @GetMapping("/{jobId}")
    public ResponseGeneral<?> getJobDetail(@PathVariable("jobId") UUID jobId){
        return jobService.getJobDetail(jobId);
    }
}
