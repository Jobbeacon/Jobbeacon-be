package com.jobbeacon.dto.request.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
public class AdminJobUpdateRequest {

  @NotBlank(message = "admin_job_title_required")
  @Size(max = 255, message = "admin_job_title_too_long")
  private String title;

  @NotNull(message = "admin_job_company_required")
  private UUID companyId;

  private String employmentType;

  @PositiveOrZero(message = "admin_job_invalid_experience")
  private Integer experienceMonths;

  private List<UUID> categoryIds;

  private List<UUID> skillIds;

  private List<String> locations;

  @PositiveOrZero(message = "admin_job_invalid_salary")
  private Long minSalary;

  @PositiveOrZero(message = "admin_job_invalid_salary")
  private Long maxSalary;

  @Size(max = 100)
  private String currency;

  @Size(max = 100)
  private String salaryPeriod;

  private Instant postedDate;

  private Instant expiredDate;

  private String description;

  private String benefits;
}
