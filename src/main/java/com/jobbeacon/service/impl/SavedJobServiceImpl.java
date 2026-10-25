package com.jobbeacon.service.impl;

import com.jobbeacon.dto.request.PageRequestDto;
import com.jobbeacon.dto.response.PageResponse;
import com.jobbeacon.dto.response.SavedJobResponse;
import com.jobbeacon.exception.base.NotFoundException;
import com.jobbeacon.repository.JobRepository;
import com.jobbeacon.repository.UserSavedJobRepository;
import com.jobbeacon.service.SavedJobService;
import com.jobbeacon.utils.PageUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SavedJobServiceImpl implements SavedJobService {

  private static final String JOB_NOT_FOUND_CODE = "job_not_found";

  private final UserSavedJobRepository userSavedJobRepository;
  private final JobRepository jobRepository;

  @Override
  @Transactional
  public void saveJob(UUID userId, UUID jobId) {
    if (!jobRepository.existsById(jobId)) {
      throw new NotFoundException(JOB_NOT_FOUND_CODE);
    }
    userSavedJobRepository.saveIfAbsent(userId, jobId);
  }

  @Override
  @Transactional
  public void unsaveJob(UUID userId, UUID jobId) {
    userSavedJobRepository.deleteByUserIdAndJobId(userId, jobId);
  }

  @Override
  @Transactional(readOnly = true)
  public PageResponse<SavedJobResponse> getSavedJobs(UUID userId, PageRequestDto pageRequest) {
    Pageable requested = PageUtils.buildPageable(pageRequest);
    Pageable pageable = PageRequest.of(requested.getPageNumber(), requested.getPageSize());
    Page<SavedJobResponse> data = userSavedJobRepository.findSavedJobs(userId, pageable);
    return PageResponse.of(data.getContent(), Math.toIntExact(data.getTotalElements()));
  }

  @Override
  @Transactional(readOnly = true)
  public List<UUID> getSavedJobIds(UUID userId) {
    return userSavedJobRepository.findJobIdsByUserId(userId);
  }
}
