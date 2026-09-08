package com.jobbeacon.service;

import com.jobbeacon.dto.message.JobPostingMessage;

import com.jobbeacon.dto.request.JobSearchRequest;
import com.jobbeacon.dto.response.JobSummaryResponse;
import com.jobbeacon.dto.response.PageResponse;
import com.jobbeacon.dto.response.ResponseGeneral;
import com.jobbeacon.entity.Job;
import com.jobbeacon.service.base.BaseService;

import java.util.List;

public interface JobService extends BaseService<Job> {
    void processJobPostingMessage(JobPostingMessage message);
    ResponseGeneral<PageResponse<JobSummaryResponse>> searchJobs(JobSearchRequest jobSearchRequest);
}
