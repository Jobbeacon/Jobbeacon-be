package com.jobbeacon.service.admin.impl;

import com.jobbeacon.constant.UserRole;
import com.jobbeacon.constant.UserStatus;
import com.jobbeacon.dto.request.admin.AdminPageQuery;
import com.jobbeacon.dto.request.admin.AdminUserUpdateRequest;
import com.jobbeacon.dto.response.PageResponse;
import com.jobbeacon.dto.response.admin.AdminUserDetailResponse;
import com.jobbeacon.dto.response.admin.AdminUserRowResponse;
import com.jobbeacon.dto.response.admin.AdminUserStatsResponse;
import com.jobbeacon.entity.CvProfile;
import com.jobbeacon.entity.CvSkillMapping;
import com.jobbeacon.entity.Province;
import com.jobbeacon.entity.User;
import com.jobbeacon.entity.UserJobPreference;
import com.jobbeacon.exception.base.BadRequestException;
import com.jobbeacon.exception.base.ConflictException;
import com.jobbeacon.exception.base.NotFoundException;
import com.jobbeacon.repository.CvProfileRepository;
import com.jobbeacon.repository.CvSkillMappingRepository;
import com.jobbeacon.repository.UserJobPreferenceRepository;
import com.jobbeacon.repository.UserRepository;
import com.jobbeacon.service.admin.AdminUserService;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminUserServiceImpl implements AdminUserService {

  private static final String USER_NOT_FOUND = "admin_user_not_found";

  private final UserRepository userRepository;
  private final CvProfileRepository cvProfileRepository;
  private final CvSkillMappingRepository cvSkillMappingRepository;
  private final UserJobPreferenceRepository userJobPreferenceRepository;

  @Override
  @Transactional(readOnly = true)
  public AdminUserStatsResponse stats() {
    return new AdminUserStatsResponse(
        userRepository.count(),
        userRepository.countByRole(UserRole.ADMIN),
        userRepository.countByStatus(UserStatus.ACTIVE),
        userRepository.countByStatus(UserStatus.SUSPENDED),
        cvProfileRepository.count()
    );
  }

  @Override
  @Transactional(readOnly = true)
  public PageResponse<AdminUserRowResponse> list(String q, UserRole role, UserStatus status, AdminPageQuery page) {
    Specification<User> spec = (root, query, cb) -> {
      List<Predicate> predicates = new ArrayList<>();
      if (q != null && !q.isBlank()) {
        String pattern = "%" + q.trim().toLowerCase(Locale.ROOT)
            .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        predicates.add(cb.or(
            cb.like(cb.lower(root.get("email")), pattern, '\\'),
            cb.like(cb.lower(root.get("displayName")), pattern, '\\')));
      }
      if (role != null) {
        predicates.add(cb.equal(root.get("role"), role));
      }
      if (status != null) {
        predicates.add(cb.equal(root.get("status"), status));
      }
      return cb.and(predicates.toArray(Predicate[]::new));
    };
    Page<User> users = userRepository.findAll(spec, PageRequest.of(page.page() - 1, page.size(),
        Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("id"))));
    List<UUID> ids = users.getContent().stream().map(User::getId).toList();
    Set<UUID> withCv = ids.isEmpty() ? Set.of() : new HashSet<>(cvProfileRepository.findUserIdsWithProfile(ids));
    List<AdminUserRowResponse> rows = users.getContent().stream()
        .map(user -> toRow(user, withCv.contains(user.getId())))
        .toList();
    return PageResponse.of(rows, (int) Math.min(users.getTotalElements(), Integer.MAX_VALUE));
  }

  @Override
  @Transactional(readOnly = true)
  public AdminUserDetailResponse detail(UUID id) {
    User user = userRepository.findById(id).orElseThrow(() -> new NotFoundException(USER_NOT_FOUND));
    AdminUserDetailResponse.Cv cv = cvProfileRepository.findByUserId(id).map(this::toCv).orElse(null);
    AdminUserDetailResponse.Preference preference = userJobPreferenceRepository.findByUserId(id)
        .map(AdminUserServiceImpl::toPreference)
        .orElse(null);
    return new AdminUserDetailResponse(
        user.getId(), user.getEmail(), user.getDisplayName(), user.getAvatarUrl(), user.getRole(),
        user.getStatus(), cv != null, user.getCreatedAt(), user.getLastLoginAt(), cv, preference);
  }

  @Override
  @Transactional
  public AdminUserDetailResponse update(UUID id, AdminUserUpdateRequest request) {
    log.info("(admin update user) id={} role={} status={}", id, request.getRole(), request.getStatus());
    User user = userRepository.findById(id).orElseThrow(() -> new NotFoundException(USER_NOT_FOUND));
    UserRole nextRole = request.getRole() == null ? user.getRole()
        : parse(UserRole.class, request.getRole(), "admin_user_invalid_role");
    UserStatus nextStatus = request.getStatus() == null ? user.getStatus()
        : parse(UserStatus.class, request.getStatus(), "admin_user_invalid_status");

    if (AdminUserRules.isActiveAdmin(user.getRole(), user.getStatus())
        && !AdminUserRules.isActiveAdmin(nextRole, nextStatus)) {
      long others = userRepository.countByRoleAndStatusAndIdNot(UserRole.ADMIN, UserStatus.ACTIVE, id);
      if (AdminUserRules.removesLastActiveAdmin(user.getRole(), user.getStatus(), nextRole, nextStatus, others)) {
        throw new ConflictException("admin_last_active_admin");
      }
    }

    if (request.getDisplayName() != null && !request.getDisplayName().isBlank()) {
      user.setDisplayName(request.getDisplayName().trim());
    }
    user.setRole(nextRole);
    user.setStatus(nextStatus);
    userRepository.save(user);
    return detail(id);
  }

  private static <E extends Enum<E>> E parse(Class<E> type, String value, String errorCode) {
    try {
      return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException e) {
      throw new BadRequestException(errorCode);
    }
  }

  private static AdminUserRowResponse toRow(User user, boolean hasCv) {
    return new AdminUserRowResponse(user.getId(), user.getEmail(), user.getDisplayName(), user.getAvatarUrl(),
        user.getRole(), user.getStatus(), hasCv, user.getCreatedAt(), user.getLastLoginAt());
  }

  private AdminUserDetailResponse.Cv toCv(CvProfile profile) {
    List<String> skills = cvSkillMappingRepository.findByIdCvProfileId(profile.getId()).stream()
        .map(CvSkillMapping::getSkill)
        .filter(Objects::nonNull)
        .map(skill -> skill.getName())
        .filter(Objects::nonNull)
        .sorted(String.CASE_INSENSITIVE_ORDER)
        .toList();
    return new AdminUserDetailResponse.Cv(profile.getCurrentTitle(), profile.getTotalMonthsExperience(), skills,
        profile.getUpdatedAt());
  }

  private static AdminUserDetailResponse.Preference toPreference(UserJobPreference preference) {
    return new AdminUserDetailResponse.Preference(
        preference.getDesiredTitles() == null ? List.of() : List.copyOf(preference.getDesiredTitles()),
        preference.getEmploymentTypes() == null ? List.of() : List.copyOf(preference.getEmploymentTypes()),
        preference.getProvinces().stream().map(Province::getNameLocal).sorted().toList(),
        preference.getExpectedSalaryMin(),
        preference.getExpectedSalaryMax(),
        preference.getCurrency() == null ? null : preference.getCurrency().name(),
        preference.getSalaryPeriod() == null ? null : preference.getSalaryPeriod().name(),
        preference.isSalaryNegotiable(),
        preference.isRemoteOk(),
        preference.isOpenToWork()
    );
  }
}
