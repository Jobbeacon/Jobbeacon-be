package com.jobbeacon.service.admin;

import com.jobbeacon.dto.request.admin.AdminCompanyUpdateRequest;
import com.jobbeacon.dto.request.admin.AdminPageQuery;
import com.jobbeacon.dto.response.PageResponse;
import com.jobbeacon.dto.response.admin.AdminCompanyDetailResponse;
import com.jobbeacon.dto.response.admin.AdminCompanyRowResponse;
import com.jobbeacon.dto.response.admin.AdminCompanyStatsResponse;

import java.util.UUID;

public interface AdminCompanyService {

  AdminCompanyStatsResponse stats();

  PageResponse<AdminCompanyRowResponse> list(String q, String sort, AdminPageQuery page);

  AdminCompanyDetailResponse detail(UUID id);

  AdminCompanyDetailResponse update(UUID id, AdminCompanyUpdateRequest request);

  AdminCompanyDetailResponse merge(UUID sourceId, UUID targetId);

  void delete(UUID id);
}
