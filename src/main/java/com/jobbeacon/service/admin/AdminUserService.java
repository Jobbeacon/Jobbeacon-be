package com.jobbeacon.service.admin;

import com.jobbeacon.constant.UserRole;
import com.jobbeacon.constant.UserStatus;
import com.jobbeacon.dto.request.admin.AdminPageQuery;
import com.jobbeacon.dto.request.admin.AdminUserUpdateRequest;
import com.jobbeacon.dto.response.PageResponse;
import com.jobbeacon.dto.response.admin.AdminUserDetailResponse;
import com.jobbeacon.dto.response.admin.AdminUserRowResponse;
import com.jobbeacon.dto.response.admin.AdminUserStatsResponse;

import java.util.UUID;

public interface AdminUserService {

  AdminUserStatsResponse stats();

  PageResponse<AdminUserRowResponse> list(String q, UserRole role, UserStatus status, AdminPageQuery page);

  AdminUserDetailResponse detail(UUID id);

  AdminUserDetailResponse update(UUID id, AdminUserUpdateRequest request);
}
