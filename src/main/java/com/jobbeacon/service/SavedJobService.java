package com.jobbeacon.service;

import com.jobbeacon.dto.request.PageRequestDto;
import com.jobbeacon.dto.response.PageResponse;
import com.jobbeacon.dto.response.SavedJobResponse;
import java.util.List;
import java.util.UUID;

public interface SavedJobService {

  void saveJob(UUID userId, UUID jobId);

  void unsaveJob(UUID userId, UUID jobId);

  PageResponse<SavedJobResponse> getSavedJobs(UUID userId, PageRequestDto pageRequest);

  List<UUID> getSavedJobIds(UUID userId);
}
