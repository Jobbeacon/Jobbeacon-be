package com.jobbeacon.dto.request.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AdminCompanyUpdateRequest {

  @NotBlank(message = "admin_company_name_required")
  @Size(max = 255, message = "admin_company_name_too_long")
  private String name;

  private String logoUrl;
}
