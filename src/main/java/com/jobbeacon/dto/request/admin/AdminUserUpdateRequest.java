package com.jobbeacon.dto.request.admin;

import jakarta.validation.constraints.Size;
import lombok.Data;

/** Role and status are strings so that an unknown value becomes a 400 with an i18n key. */
@Data
public class AdminUserUpdateRequest {

  @Size(max = 150, message = "admin_user_name_too_long")
  private String displayName;

  private String role;

  private String status;
}
