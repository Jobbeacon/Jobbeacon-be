package com.jobbeacon.service.impl;

import com.jobbeacon.dto.cv.CvParsedPayload;
import com.jobbeacon.entity.CvProfile;
import com.jobbeacon.entity.CvSkillMapping;
import com.jobbeacon.entity.Skill;
import com.jobbeacon.event.CvProfilePublishedEvent;
import com.jobbeacon.exception.base.NotFoundException;
import com.jobbeacon.graph.GraphSyncOutboxService;
import com.jobbeacon.repository.CvProfileRepository;
import com.jobbeacon.repository.CvSkillMappingRepository;
import com.jobbeacon.repository.SkillRepository;
import com.jobbeacon.service.CvProfileService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Service
public class CvProfileServiceImpl implements CvProfileService {

  static final String CV_PROFILE_NOT_FOUND = "cv_profile_not_found";
  static final String CV_PROFILE_SKILL_NOT_FOUND = "cv_profile_skill_not_found";
  static final int EVIDENCE_MAX_LENGTH = 500;
  static final int SAVE_ATTEMPTS = 2;

  private final CvProfileRepository cvProfileRepository;
  private final CvSkillMappingRepository cvSkillMappingRepository;
  private final SkillRepository skillRepository;
  private final ApplicationEventPublisher eventPublisher;
  private final GraphSyncOutboxService graphSyncOutboxService;
  private final TransactionTemplate transactionTemplate;
  private final Clock clock;

  @Autowired
  public CvProfileServiceImpl(
      CvProfileRepository cvProfileRepository,
      CvSkillMappingRepository cvSkillMappingRepository,
      SkillRepository skillRepository,
      ApplicationEventPublisher eventPublisher,
      GraphSyncOutboxService graphSyncOutboxService,
      PlatformTransactionManager transactionManager
  ) {
    this(cvProfileRepository, cvSkillMappingRepository, skillRepository, eventPublisher, graphSyncOutboxService,
        transactionManager, Clock.systemUTC());
  }

  CvProfileServiceImpl(
      CvProfileRepository cvProfileRepository,
      CvSkillMappingRepository cvSkillMappingRepository,
      SkillRepository skillRepository,
      ApplicationEventPublisher eventPublisher,
      GraphSyncOutboxService graphSyncOutboxService,
      PlatformTransactionManager transactionManager,
      Clock clock
  ) {
    this.cvProfileRepository = cvProfileRepository;
    this.cvSkillMappingRepository = cvSkillMappingRepository;
    this.skillRepository = skillRepository;
    this.eventPublisher = eventPublisher;
    this.graphSyncOutboxService = graphSyncOutboxService;
    this.transactionTemplate = new TransactionTemplate(transactionManager);
    // Own transaction per attempt: a failed attempt leaves its transaction rollback-only, so the
    // retry must not join it (nor a caller's transaction).
    this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    this.clock = clock;
  }

  /**
   * Not {@code @Transactional}: each attempt runs in its own transaction via the template. Two first
   * uploads of the same user can race on {@code uq_cv_profile_user} (or on the mapping primary key);
   * the loser retries once and then sees the winner's row, so the last writer wins.
   */
  @Override
  public CvProfileView saveParsed(UUID userId, CvParsedPayload payload, String parserModelVersion) {
    CvParsedPayload safePayload = payload != null ? payload : CvParsedPayload.empty();
    for (int attempt = 1; ; attempt++) {
      try {
        return transactionTemplate.execute(status -> upsert(userId, safePayload, parserModelVersion));
      } catch (DataIntegrityViolationException e) {
        if (attempt >= SAVE_ATTEMPTS) {
          throw e;
        }
        log.warn("(saveParsed) userId: {}, stage: save_profile, status: conflict_retry, attempt: {}",
            userId, attempt);
      }
    }
  }

  @Override
  @Transactional(readOnly = true)
  public CvProfileView getProfile(UUID userId) {
    log.info("(getProfile) userId: {}", userId);
    CvProfile profile = findProfile(userId);
    return new CvProfileView(profile, loadSkills(profile.getId()));
  }

  @Override
  @Transactional
  public void deleteProfile(UUID userId) {
    log.info("(deleteProfile) userId: {}", userId);
    if (cvProfileRepository.deleteByUserId(userId) == 0) {
      throw new NotFoundException(CV_PROFILE_NOT_FOUND);
    }
    graphSyncOutboxService.candidateChanged(userId);
  }

  @Override
  @Transactional(readOnly = true)
  public CvProfileSkills getSkills(UUID userId) {
    log.info("(getSkills) userId: {}", userId);
    CvProfile profile = findProfile(userId);
    return new CvProfileSkills(profile.getId(), loadSkills(profile.getId()));
  }

  @Override
  @Transactional
  public void deleteSkill(UUID userId, UUID skillId) {
    log.info("(deleteSkill) userId: {}, skillId: {}", userId, skillId);
    CvProfile profile = findProfile(userId);
    if (cvSkillMappingRepository.deleteByCvProfileIdAndSkillId(profile.getId(), skillId) == 0) {
      throw new NotFoundException(CV_PROFILE_SKILL_NOT_FOUND);
    }
    graphSyncOutboxService.candidateChanged(userId);
  }

  private CvProfile findProfile(UUID userId) {
    return cvProfileRepository.findByUserId(userId)
        .orElseThrow(() -> new NotFoundException(CV_PROFILE_NOT_FOUND));
  }

  private List<SkillEntry> loadSkills(UUID profileId) {
    List<CvSkillMapping> mappings = cvSkillMappingRepository.findByIdCvProfileId(profileId);
    List<UUID> skillIds = mappings.stream().map(m -> m.getId().getSkillId()).toList();
    Map<UUID, String> names = skillRepository.findAllById(skillIds).stream()
        .collect(Collectors.toMap(Skill::getId, s -> Objects.toString(s.getName(), ""), (a, b) -> a));
    return mappings.stream()
        .map(m -> toEntry(m, names.get(m.getId().getSkillId())))
        .toList();
  }

  private CvProfileView upsert(UUID userId, CvParsedPayload payload, String parserModelVersion) {
    Instant now = clock.instant();
    CvProfile profile = cvProfileRepository.findByUserId(userId).orElseGet(CvProfile::new);
    applyPayload(profile, userId, payload, parserModelVersion, now);
    CvProfile saved = cvProfileRepository.saveAndFlush(profile);

    // Bulk delete first: old mappings must not be loaded into the persistence context before
    // re-inserting rows with the same keys.
    cvSkillMappingRepository.deleteByCvProfileId(saved.getId());
    List<CvSkillMapping> mappings = buildMappings(saved, payload.skills());
    cvSkillMappingRepository.saveAllAndFlush(mappings);
    graphSyncOutboxService.candidateChanged(userId);

    // Delivered to @TransactionalEventListener(AFTER_COMMIT) listeners only once the transaction commits.
    eventPublisher.publishEvent(new CvProfilePublishedEvent(userId, saved.getId()));
    log.info("(saveParsed) saved, userId: {}, profileId: {}, skills: {}", userId, saved.getId(),
        mappings.size());
    return new CvProfileView(saved, mappings.stream().map(CvProfileServiceImpl::toEntry).toList());
  }

  private static void applyPayload(CvProfile profile, UUID userId, CvParsedPayload payload,
                                   String parserModelVersion, Instant now) {
    profile.setUserId(userId);
    profile.setCurrentTitle(payload.currentTitle());
    profile.setSummary(payload.summary());
    profile.setTotalMonthsExperience(payload.totalMonthsExperience());
    profile.setExperiences(copyOrEmpty(payload.experiences()));
    profile.setEducations(copyOrEmpty(payload.educations()));
    profile.setParserModelVersion(parserModelVersion);
    profile.setUploadedAt(now);
    profile.setParsedAt(now);
  }

  private List<CvSkillMapping> buildMappings(CvProfile profile, List<CvParsedPayload.SkillItem> items) {
    if (items == null || items.isEmpty()) {
      return List.of();
    }
    Map<UUID, CvParsedPayload.SkillItem> bySkillId = new LinkedHashMap<>();
    for (CvParsedPayload.SkillItem item : items) {
      if (item == null || item.skillId() == null) {
        continue;
      }
      bySkillId.merge(item.skillId(), item, CvProfileServiceImpl::higherConfidence);
    }
    if (bySkillId.isEmpty()) {
      return List.of();
    }
    Map<UUID, Skill> skills = skillRepository.findAllById(bySkillId.keySet()).stream()
        .collect(Collectors.toMap(Skill::getId, Function.identity(), (a, b) -> a));
    List<CvSkillMapping> mappings = new ArrayList<>();
    bySkillId.forEach((skillId, item) -> {
      Skill skill = skills.get(skillId);
      if (skill != null) {
        mappings.add(toMapping(profile, skill, item));
      }
    });
    return mappings;
  }

  private static CvSkillMapping toMapping(CvProfile profile, Skill skill, CvParsedPayload.SkillItem item) {
    CvSkillMapping mapping = new CvSkillMapping();
    mapping.setId(new CvSkillMapping.CvSkillMappingId(profile.getId(), skill.getId()));
    mapping.setCvProfile(profile);
    mapping.setSkill(skill);
    mapping.setConfidence(toConfidence(item.confidence()));
    mapping.setExperienceMonths(toExperienceMonths(item.experienceMonths()));
    mapping.setLastUsedAt(toLastUsedAt(item.lastUsedAt()));
    mapping.setEvidenceText(toEvidenceText(item.evidence()));
    return mapping;
  }

  private static SkillEntry toEntry(CvSkillMapping mapping) {
    return toEntry(mapping, mapping.getSkill() != null ? mapping.getSkill().getName() : null);
  }

  private static SkillEntry toEntry(CvSkillMapping mapping, String skillName) {
    return new SkillEntry(
        mapping.getId().getSkillId(),
        skillName,
        mapping.getConfidence(),
        mapping.getExperienceMonths(),
        mapping.getLastUsedAt(),
        mapping.getEvidenceText()
    );
  }

  private static CvParsedPayload.SkillItem higherConfidence(CvParsedPayload.SkillItem a,
                                                            CvParsedPayload.SkillItem b) {
    double ca = a.confidence() != null ? a.confidence() : -1;
    double cb = b.confidence() != null ? b.confidence() : -1;
    return cb > ca ? b : a;
  }

  private static BigDecimal toConfidence(Double value) {
    if (value == null || value.isNaN()) {
      return null;
    }
    double clamped = Math.max(0d, Math.min(1d, value));
    return BigDecimal.valueOf(clamped).setScale(3, RoundingMode.HALF_UP);
  }

  private static Integer toExperienceMonths(Integer value) {
    return value != null && value >= 0 ? value : null;
  }

  private static LocalDate toLastUsedAt(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    String trimmed = value.trim();
    try {
      return YearMonth.parse(trimmed).atDay(1);
    } catch (DateTimeParseException e) {
      try {
        return LocalDate.parse(trimmed);
      } catch (DateTimeParseException ignored) {
        return null;
      }
    }
  }

  private static String toEvidenceText(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    String trimmed = value.trim();
    if (trimmed.length() <= EVIDENCE_MAX_LENGTH) {
      return trimmed;
    }
    int end = EVIDENCE_MAX_LENGTH;
    if (Character.isHighSurrogate(trimmed.charAt(end - 1))) {
      end--;
    }
    return trimmed.substring(0, end);
  }

  private static <T> List<T> copyOrEmpty(List<T> list) {
    if (list == null) {
      return new ArrayList<>();
    }
    return list.stream().filter(Objects::nonNull).collect(Collectors.toCollection(ArrayList::new));
  }
}
