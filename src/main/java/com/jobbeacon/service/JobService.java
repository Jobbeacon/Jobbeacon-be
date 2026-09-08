package com.jobbeacon.service;

import com.jobbeacon.dto.message.JobPostingMessage;

import com.jobbeacon.entity.Job;
import com.jobbeacon.service.base.BaseService;

public interface JobService extends BaseService<Job> {
    void processJobPostingMessage(JobPostingMessage message);
}
