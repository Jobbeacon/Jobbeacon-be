package com.jobbeacon.dto.request.admin;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class AdminMergeRequest {

  @NotNull(message = "admin_merge_target_required")
  private UUID targetId;
}
