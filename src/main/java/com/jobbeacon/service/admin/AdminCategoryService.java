package com.jobbeacon.service.admin;

import com.jobbeacon.dto.request.admin.AdminCategoryRequest;
import com.jobbeacon.dto.response.admin.AdminCategoryNodeResponse;

import java.util.List;
import java.util.UUID;

public interface AdminCategoryService {

  List<AdminCategoryNodeResponse> tree();

  AdminCategoryNodeResponse create(AdminCategoryRequest request);

  AdminCategoryNodeResponse rename(UUID id, AdminCategoryRequest request);

  void delete(UUID id);
}
