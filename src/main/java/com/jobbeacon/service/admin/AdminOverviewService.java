package com.jobbeacon.service.admin;

import com.jobbeacon.dto.response.admin.AdminOverviewResponse;

public interface AdminOverviewService {

  AdminOverviewResponse overview(int days);
}
