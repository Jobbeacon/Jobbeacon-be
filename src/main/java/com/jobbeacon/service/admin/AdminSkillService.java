package com.jobbeacon.service.admin;

import com.jobbeacon.dto.request.admin.AdminPageQuery;
import com.jobbeacon.dto.request.admin.AdminSkillRequest;
import com.jobbeacon.dto.response.PageResponse;
import com.jobbeacon.dto.response.admin.AdminSkillResponse;
import com.jobbeacon.dto.response.admin.AdminSkillStatsResponse;

import java.util.UUID;

public interface AdminSkillService {

  AdminSkillStatsResponse stats();

  PageResponse<AdminSkillResponse> list(String q, String filter, String sort, AdminPageQuery page);

  AdminSkillResponse create(AdminSkillRequest request);

  AdminSkillResponse update(UUID id, AdminSkillRequest request);

  AdminSkillResponse merge(UUID sourceId, UUID targetId);

  void delete(UUID id);
}
