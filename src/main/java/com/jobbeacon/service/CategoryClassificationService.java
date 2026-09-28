package com.jobbeacon.service;

import com.jobbeacon.dto.message.JobPostingMessage;
import com.jobbeacon.entity.Category;

import java.util.Optional;

public interface CategoryClassificationService {
    Optional<Category> classifyDeepestCategory(JobPostingMessage message);
}
