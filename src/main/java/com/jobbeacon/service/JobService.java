package com.jobbeacon.service;

import com.jobbeacon.dto.message.JobPostingMessage;

import com.jobbeacon.dto.request.JobSearchRequest;
import com.jobbeacon.dto.response.JobDetailResponse;
import com.jobbeacon.dto.response.JobSummaryResponse;
import com.jobbeacon.dto.response.PageResponse;
import com.jobbeacon.dto.response.ResponseGeneral;
import com.jobbeacon.entity.Job;
import com.jobbeacon.service.base.BaseService;

import java.util.List;
import java.util.UUID;

public interface JobService {
    void processJobPostingMessage(JobPostingMessage message);
    ResponseGeneral<PageResponse<JobSummaryResponse>> searchJobs(JobSearchRequest jobSearchRequest);
    ResponseGeneral<JobDetailResponse> getJobDetail(UUID jobId);

}
