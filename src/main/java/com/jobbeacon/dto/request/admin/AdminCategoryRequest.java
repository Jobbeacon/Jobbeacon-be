package com.jobbeacon.dto.request.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.UUID;

@Data
public class AdminCategoryRequest {

  @NotBlank(message = "admin_category_name_required")
  @Size(max = 255, message = "admin_category_name_too_long")
  private String name;

  /** Only read on create; null creates a top-level group. */
  private UUID parentId;
}
