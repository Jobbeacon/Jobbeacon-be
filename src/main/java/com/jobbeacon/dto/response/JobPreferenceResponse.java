package com.jobbeacon.dto.response;

import com.jobbeacon.constant.SalaryCurrency;
import com.jobbeacon.constant.SalaryPeriod;
import com.jobbeacon.entity.Province;
import com.jobbeacon.entity.UserJobPreference;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** {@code id} is null while the user has not saved a preference yet (defaults are returned). */
public record JobPreferenceResponse(
    UUID id,
    List<String> desiredTitles,
    List<LocationItemResponse> provinces,
    List<String> employmentTypes,
    boolean remoteOk,
    Long expectedSalaryMin,
    Long expectedSalaryMax,
    SalaryCurrency currency,
    SalaryPeriod salaryPeriod,
    boolean salaryNegotiable,
    boolean openToWork,
    Instant updatedAt
) {

  /** Must be called while {@code provinces} is loaded (inside the transaction or fetched eagerly). */
  public static JobPreferenceResponse from(UserJobPreference preference) {
    List<LocationItemResponse> provinces = preference.getProvinces() == null ? List.of()
        : preference.getProvinces().stream()
            .sorted(Comparator.comparing(Province::getId))
            .map(p -> new LocationItemResponse(p.getId(), p.getNameLocal()))
            .toList();
    return new JobPreferenceResponse(
        preference.getId(),
        preference.getDesiredTitles() == null ? List.of() : List.copyOf(preference.getDesiredTitles()),
        provinces,
        preference.getEmploymentTypes() == null ? List.of() : List.copyOf(preference.getEmploymentTypes()),
        preference.isRemoteOk(),
        preference.getExpectedSalaryMin(),
        preference.getExpectedSalaryMax(),
        preference.getCurrency(),
        preference.getSalaryPeriod(),
        preference.isSalaryNegotiable(),
        preference.isOpenToWork(),
        preference.getUpdatedAt()
    );
  }
}
