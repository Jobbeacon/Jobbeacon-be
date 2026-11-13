package com.jobbeacon.service.admin.impl;

import com.jobbeacon.client.GeminiEmbeddingClient;
import com.jobbeacon.dto.request.admin.AdminPageQuery;
import com.jobbeacon.dto.request.admin.AdminSkillRequest;
import com.jobbeacon.dto.response.PageResponse;
import com.jobbeacon.dto.response.admin.AdminSkillResponse;
import com.jobbeacon.dto.response.admin.AdminSkillStatsResponse;
import com.jobbeacon.entity.Skill;
import com.jobbeacon.entity.SkillAlias;
import com.jobbeacon.exception.base.BadRequestException;
import com.jobbeacon.exception.base.ConflictException;
import com.jobbeacon.exception.base.NotFoundException;
import com.jobbeacon.repository.SkillAliasRepository;
import com.jobbeacon.repository.SkillRepository;
import com.jobbeacon.repository.admin.AdminSkillQueryRepository;
import com.jobbeacon.search.JobSearchReindexer;
import com.jobbeacon.service.admin.AdminSkillService;
import com.jobbeacon.service.impl.SkillResolutionServiceImpl;
import com.jobbeacon.utils.SkillUtils;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminSkillServiceImpl implements AdminSkillService {

  private static final String SKILL_NOT_FOUND = "skill_not_found";
  private static final Duration RECENT = Duration.ofDays(7);

  private final AdminSkillQueryRepository queryRepository;
  private final SkillRepository skillRepository;
  private final SkillAliasRepository skillAliasRepository;
  private final GeminiEmbeddingClient geminiEmbeddingClient;
  private final JobSearchReindexer jobSearchReindexer;
  private final EntityManager entityManager;

  @Override
  @Transactional(readOnly = true)
  public AdminSkillStatsResponse stats() {
    return queryRepository.stats(Instant.now().minus(RECENT));
  }

  @Override
  @Transactional(readOnly = true)
  public PageResponse<AdminSkillResponse> list(String q, String filter, String sort, AdminPageQuery page) {
    Instant since = Instant.now().minus(RECENT);
    long total = queryRepository.count(q, filter, since);
    List<AdminSkillResponse> rows = total == 0 ? List.of() : queryRepository.find(q, filter, sort, since, page);
    return PageResponse.of(rows, (int) Math.min(total, Integer.MAX_VALUE));
  }

  @Override
  @Transactional
  public AdminSkillResponse create(AdminSkillRequest request) {
    String name = request.getName().trim();
    String normalized = SkillUtils.normalizeSkill(name);
    Map<String, String> aliases = cleanAliases(request.getAliases(), normalized);
    ensureFree(normalized, null);
    aliases.keySet().forEach(alias -> ensureFree(alias, null));

    Skill skill = new Skill();
    skill.setName(name);
    skill.setNormalizedName(normalized);
    skill.setEmbedding(embed(name));
    skill = skillRepository.saveAndFlush(skill);
    saveAliases(skill, aliases);
    log.info("(admin create skill) id={} name={} aliases={}", skill.getId(), name, aliases.size());
    return reload(skill.getId());
  }

  @Override
  @Transactional
  public AdminSkillResponse update(UUID id, AdminSkillRequest request) {
    Skill skill = skillRepository.findById(id).orElseThrow(() -> new NotFoundException(SKILL_NOT_FOUND));
    String name = request.getName().trim();
    String normalized = SkillUtils.normalizeSkill(name);
    Map<String, String> aliases = cleanAliases(request.getAliases(), normalized);
    ensureFree(normalized, id);
    aliases.keySet().forEach(alias -> ensureFree(alias, id));

    boolean renamed = !name.equals(skill.getName());
    if (!normalized.equals(skill.getNormalizedName())) {
      skill.setEmbedding(embed(name));
    }
    skill.setName(name);
    skill.setNormalizedName(normalized);
    // aliases are replaced with a direct delete so re-adding an existing alias does not hit the unique index
    entityManager.flush();
    queryRepository.deleteAliases(id);
    skillRepository.saveAndFlush(skill);
    saveAliases(skill, aliases);
    log.info("(admin update skill) id={} name={} aliases={}", id, name, aliases.size());
    if (renamed) {
      jobSearchReindexer.reindex(queryRepository.jobIds(id));
    }
    return reload(id);
  }

  @Override
  @Transactional
  public AdminSkillResponse merge(UUID sourceId, UUID targetId) {
    if (sourceId.equals(targetId)) {
      throw new BadRequestException("admin_merge_same_target");
    }
    Skill source = skillRepository.findById(sourceId).orElseThrow(() -> new NotFoundException(SKILL_NOT_FOUND));
    Skill target = skillRepository.findById(targetId).orElseThrow(() -> new NotFoundException(SKILL_NOT_FOUND));
    String targetNormalized = target.getNormalizedName() != null
        ? target.getNormalizedName() : SkillUtils.normalizeSkill(target.getName());

    List<String> names = new ArrayList<>();
    names.add(source.getName());
    names.addAll(queryRepository.aliases(List.of(sourceId)).getOrDefault(sourceId, List.of()));
    List<String> targetAliases = queryRepository.aliases(List.of(targetId)).getOrDefault(targetId, List.of());
    Map<String, String> moved = cleanAliases(names, targetNormalized);
    targetAliases.forEach(alias -> moved.remove(SkillUtils.normalizeSkill(alias)));

    List<UUID> jobIds = queryRepository.jobIds(sourceId);
    entityManager.flush();
    queryRepository.moveMappings(sourceId, targetId);
    queryRepository.deleteAliases(sourceId);
    skillRepository.delete(source);
    skillRepository.flush();
    saveAliases(target, moved);
    log.info("(admin merge skill) source={} target={} aliases={} jobs={}", sourceId, targetId, moved.size(),
        jobIds.size());
    jobSearchReindexer.reindex(jobIds);
    return reload(targetId);
  }

  @Override
  @Transactional
  public void delete(UUID id) {
    Skill skill = skillRepository.findById(id).orElseThrow(() -> new NotFoundException(SKILL_NOT_FOUND));
    List<UUID> jobIds = queryRepository.jobIds(id);
    entityManager.flush();
    queryRepository.deleteMappings(id);
    queryRepository.deleteAliases(id);
    skillRepository.delete(skill);
    log.info("(admin delete skill) id={} jobs={}", id, jobIds.size());
    jobSearchReindexer.reindex(jobIds);
  }

  /** Normalized key → display text, without blanks, duplicates or the skill's own name. */
  static Map<String, String> cleanAliases(List<String> aliases, String ownNormalized) {
    Map<String, String> cleaned = new LinkedHashMap<>();
    if (aliases == null) {
      return cleaned;
    }
    for (String alias : aliases) {
      if (alias == null || alias.isBlank()) {
        continue;
      }
      String text = alias.trim().replaceAll("\\s+", " ");
      String key = SkillUtils.normalizeSkill(text);
      if (!key.isEmpty() && !key.equals(ownNormalized)) {
        cleaned.putIfAbsent(key, text);
      }
    }
    return cleaned;
  }

  /** A name or alias may belong to one skill only. */
  private void ensureFree(String normalized, UUID ownerId) {
    skillRepository.findByNormalizedName(normalized)
        .filter(other -> !other.getId().equals(ownerId))
        .ifPresent(other -> {
          throw new ConflictException("admin_skill_name_exists");
        });
    skillAliasRepository.findSkillByNormalizedAlias(normalized)
        .filter(other -> !other.getId().equals(ownerId))
        .ifPresent(other -> {
          throw new ConflictException("admin_skill_name_exists");
        });
  }

  private void saveAliases(Skill skill, Map<String, String> aliases) {
    for (Map.Entry<String, String> entry : aliases.entrySet()) {
      SkillAlias alias = new SkillAlias();
      alias.setSkill(skill);
      alias.setAlias(entry.getValue());
      alias.setNormalizedAlias(entry.getKey());
      skillAliasRepository.save(alias);
    }
    skillAliasRepository.flush();
  }

  private float[] embed(String name) {
    try {
      return geminiEmbeddingClient.embed(name, SkillResolutionServiceImpl.EMBEDDING_TASK_TYPE);
    } catch (Exception e) {
      log.warn("(admin skill embedding) skipped for '{}': {}", name, e.getMessage());
      return null;
    }
  }

  private AdminSkillResponse reload(UUID id) {
    entityManager.flush();
    return Objects.requireNonNull(queryRepository.findOne(id));
  }
}
