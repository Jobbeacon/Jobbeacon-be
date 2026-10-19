package com.jobbeacon.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Data;

/**
 * Full replacement of the user's job preference. Enum fields are taken as strings and checked in the
 * service so that an unknown value yields a 400 with an i18n key instead of a JSON parse error.
 * Null fields fall back to their defaults.
 */
@Data
public class JobPreferenceRequest {

  @Size(max = 5, message = "job_preference_too_many_titles")
  private List<
      @NotBlank(message = "job_preference_invalid_title")
      @Size(max = 100, message = "job_preference_title_too_long") String> desiredTitles;

  @Size(max = 5, message = "job_preference_too_many_provinces")
  private List<@NotBlank(message = "job_preference_invalid_province") String> provinceIds;

  /** Values of {@code EmploymentType}. */
  private List<String> employmentTypes;

  private Boolean remoteOk;

  @PositiveOrZero(message = "job_preference_invalid_salary")
  private Long expectedSalaryMin;

  @PositiveOrZero(message = "job_preference_invalid_salary")
  private Long expectedSalaryMax;

  /** Value of {@code SalaryCurrency}; default VND. */
  private String currency;

  /** Value of {@code SalaryPeriod}; default MONTH. */
  private String salaryPeriod;

  private Boolean salaryNegotiable;

  private Boolean openToWork;
}
