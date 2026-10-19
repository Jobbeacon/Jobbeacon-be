package com.jobbeacon.service.impl;

import com.jobbeacon.constant.EmploymentType;
import com.jobbeacon.constant.SalaryCurrency;
import com.jobbeacon.constant.SalaryPeriod;
import com.jobbeacon.dto.request.JobPreferenceRequest;
import com.jobbeacon.dto.response.JobPreferenceResponse;
import com.jobbeacon.entity.Province;
import com.jobbeacon.entity.UserJobPreference;
import com.jobbeacon.exception.base.BadRequestException;
import com.jobbeacon.repository.ProvinceRepository;
import com.jobbeacon.repository.UserJobPreferenceRepository;
import com.jobbeacon.service.JobPreferenceService;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Service
public class JobPreferenceServiceImpl implements JobPreferenceService {

  static final String INVALID_PROVINCE = "job_preference_invalid_province";
  static final String INVALID_EMPLOYMENT_TYPE = "job_preference_invalid_employment_type";
  static final String INVALID_CURRENCY = "job_preference_invalid_currency";
  static final String INVALID_SALARY_PERIOD = "job_preference_invalid_salary_period";
  static final String INVALID_SALARY_RANGE = "job_preference_invalid_salary_range";
  static final int SAVE_ATTEMPTS = 2;

  private final UserJobPreferenceRepository userJobPreferenceRepository;
  private final ProvinceRepository provinceRepository;
  private final TransactionTemplate transactionTemplate;

  public JobPreferenceServiceImpl(
      UserJobPreferenceRepository userJobPreferenceRepository,
      ProvinceRepository provinceRepository,
      PlatformTransactionManager transactionManager
  ) {
    this.userJobPreferenceRepository = userJobPreferenceRepository;
    this.provinceRepository = provinceRepository;
    this.transactionTemplate = new TransactionTemplate(transactionManager);
    // Own transaction per attempt: a failed attempt leaves its transaction rollback-only.
    this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  }

  @Override
  @Transactional(readOnly = true)
  public JobPreferenceResponse getPreference(UUID userId) {
    log.info("(getPreference) userId: {}", userId);
    return JobPreferenceResponse.from(
        userJobPreferenceRepository.findByUserId(userId).orElseGet(UserJobPreference::new));
  }

  /** Not {@code @Transactional}: each attempt runs in its own transaction via the template. */
  @Override
  public JobPreferenceResponse updatePreference(UUID userId, JobPreferenceRequest request) {
    Values values = normalize(request != null ? request : new JobPreferenceRequest());
    for (int attempt = 1; ; attempt++) {
      try {
        return transactionTemplate.execute(status -> upsert(userId, values));
      } catch (DataIntegrityViolationException e) {
        if (attempt >= SAVE_ATTEMPTS) {
          throw e;
        }
        log.warn("(updatePreference) userId: {}, status: conflict_retry, attempt: {}", userId, attempt);
      }
    }
  }

  private JobPreferenceResponse upsert(UUID userId, Values values) {
    Set<Province> provinces = loadProvinces(values.provinceIds());
    UserJobPreference preference = userJobPreferenceRepository.findByUserId(userId)
        .orElseGet(UserJobPreference::new);
    preference.setUserId(userId);
    preference.setDesiredTitles(new ArrayList<>(values.desiredTitles()));
    preference.setEmploymentTypes(new ArrayList<>(values.employmentTypes()));
    preference.setRemoteOk(values.remoteOk());
    preference.setExpectedSalaryMin(values.expectedSalaryMin());
    preference.setExpectedSalaryMax(values.expectedSalaryMax());
    preference.setCurrency(values.currency());
    preference.setSalaryPeriod(values.salaryPeriod());
    preference.setSalaryNegotiable(values.salaryNegotiable());
    preference.setOpenToWork(values.openToWork());
    if (preference.getProvinces() == null) {
      preference.setProvinces(new HashSet<>());
    }
    preference.getProvinces().clear();
    preference.getProvinces().addAll(provinces);
    UserJobPreference saved = userJobPreferenceRepository.saveAndFlush(preference);
    log.info("(updatePreference) saved, userId: {}, preferenceId: {}", userId, saved.getId());
    return JobPreferenceResponse.from(saved);
  }

  private Set<Province> loadProvinces(List<String> ids) {
    if (ids.isEmpty()) {
      return Set.of();
    }
    List<Province> found = provinceRepository.findAllById(ids);
    if (found.size() < ids.size()) {
      throw new BadRequestException(INVALID_PROVINCE);
    }
    return new HashSet<>(found);
  }

  private static Values normalize(JobPreferenceRequest request) {
    Long min = request.getExpectedSalaryMin();
    Long max = request.getExpectedSalaryMax();
    if (min != null && max != null && min > max) {
      throw new BadRequestException(INVALID_SALARY_RANGE);
    }
    SalaryCurrency currency = parseEnum(SalaryCurrency.class, request.getCurrency(), INVALID_CURRENCY);
    SalaryPeriod period = parseEnum(SalaryPeriod.class, request.getSalaryPeriod(), INVALID_SALARY_PERIOD);
    return new Values(
        distinctTitles(request.getDesiredTitles()),
        distinctProvinceIds(request.getProvinceIds()),
        employmentTypes(request.getEmploymentTypes()),
        Boolean.TRUE.equals(request.getRemoteOk()),
        min,
        max,
        currency != null ? currency : SalaryCurrency.VND,
        period != null ? period : SalaryPeriod.MONTH,
        Boolean.TRUE.equals(request.getSalaryNegotiable()),
        !Boolean.FALSE.equals(request.getOpenToWork())
    );
  }

  /** Trimmed, de-duplicated case-insensitively (first spelling wins). */
  private static List<String> distinctTitles(List<String> titles) {
    if (titles == null) {
      return List.of();
    }
    Map<String, String> byKey = new LinkedHashMap<>();
    for (String title : titles) {
      String trimmed = title.trim();
      byKey.putIfAbsent(trimmed.toLowerCase(Locale.ROOT), trimmed);
    }
    return List.copyOf(byKey.values());
  }

  private static List<String> distinctProvinceIds(List<String> ids) {
    if (ids == null) {
      return List.of();
    }
    return List.copyOf(new LinkedHashSet<>(ids.stream().map(String::trim).toList()));
  }

  private static List<String> employmentTypes(List<String> values) {
    if (values == null) {
      return List.of();
    }
    Set<String> types = new LinkedHashSet<>();
    for (String value : values) {
      EmploymentType type = parseEnum(EmploymentType.class, value, INVALID_EMPLOYMENT_TYPE);
      if (type == null) {
        throw new BadRequestException(INVALID_EMPLOYMENT_TYPE);
      }
      types.add(type.name());
    }
    return List.copyOf(types);
  }

  /** Case-insensitive; null or blank returns null so the caller can apply its default. */
  private static <E extends Enum<E>> E parseEnum(Class<E> type, String value, String errorCode) {
    if (value == null || value.isBlank()) {
      return null;
    }
    try {
      return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException e) {
      throw new BadRequestException(errorCode);
    }
  }

  private record Values(
      List<String> desiredTitles,
      List<String> provinceIds,
      List<String> employmentTypes,
      boolean remoteOk,
      Long expectedSalaryMin,
      Long expectedSalaryMax,
      SalaryCurrency currency,
      SalaryPeriod salaryPeriod,
      boolean salaryNegotiable,
      boolean openToWork
  ) {
  }
}
