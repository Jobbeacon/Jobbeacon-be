package com.jobbeacon.service.admin;

import com.jobbeacon.dto.request.admin.AdminJobUpdateRequest;
import com.jobbeacon.dto.request.admin.AdminPageQuery;
import com.jobbeacon.dto.response.PageResponse;
import com.jobbeacon.dto.response.admin.AdminJobDetailResponse;
import com.jobbeacon.dto.response.admin.AdminJobRowResponse;
import com.jobbeacon.dto.response.admin.AdminJobStatsResponse;
import com.jobbeacon.repository.admin.AdminJobQueryRepository;

import java.util.UUID;

public interface AdminJobService {

  AdminJobStatsResponse stats();

  PageResponse<AdminJobRowResponse> list(AdminJobQueryRepository.Filter filter, AdminPageQuery page);

  AdminJobDetailResponse detail(UUID id);

  AdminJobDetailResponse update(UUID id, AdminJobUpdateRequest request);

  void delete(UUID id);
}
