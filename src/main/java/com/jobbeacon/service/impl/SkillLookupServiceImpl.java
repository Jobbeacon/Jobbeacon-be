package com.jobbeacon.service.impl;

import com.jobbeacon.dto.skill.SkillRef;
import com.jobbeacon.repository.SkillAliasRepository;
import com.jobbeacon.repository.SkillAliasRepository.AliasNameRow;
import com.jobbeacon.repository.SkillRepository;
import com.jobbeacon.repository.SkillRepository.SkillNameRow;
import com.jobbeacon.service.SkillLookupService;
import com.jobbeacon.utils.SkillUtils;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class SkillLookupServiceImpl implements SkillLookupService {

  private final SkillRepository skillRepository;
  private final SkillAliasRepository skillAliasRepository;
  private final Duration ttl;
  private final Clock clock;
  private final Object reloadLock = new Object();

  private volatile CachedIndex cache;

  @Autowired
  public SkillLookupServiceImpl(
      SkillRepository skillRepository,
      SkillAliasRepository skillAliasRepository,
      @Value("${app.cv.skill-index-ttl:10m}") Duration ttl
  ) {
    this(skillRepository, skillAliasRepository, ttl, Clock.systemUTC());
  }

  SkillLookupServiceImpl(SkillRepository skillRepository, SkillAliasRepository skillAliasRepository,
      Duration ttl, Clock clock) {
    this.skillRepository = skillRepository;
    this.skillAliasRepository = skillAliasRepository;
    this.ttl = ttl;
    this.clock = clock;
  }

  @Override
  public Map<String, SkillRef> nameIndex() {
    CachedIndex current = cache;
    if (isFresh(current)) {
      return current.index();
    }
    synchronized (reloadLock) {
      current = cache;
      if (isFresh(current)) {
        return current.index();
      }
      long start = System.currentTimeMillis();
      Map<String, SkillRef> index = buildIndex(
          skillRepository.findAllNameRowsOrderByIdAsc(), skillAliasRepository.findAllNameRowsOrderBySkillIdAsc());
      cache = new CachedIndex(index, clock.instant());
      log.info("(nameIndex) loaded skill index: {} names in {} ms", index.size(),
          System.currentTimeMillis() - start);
      return index;
    }
  }

  private boolean isFresh(CachedIndex current) {
    return current != null && clock.instant().isBefore(current.loadedAt().plus(ttl));
  }

  private static Map<String, SkillRef> buildIndex(List<SkillNameRow> skills, List<AliasNameRow> aliases) {
    Map<String, SkillRef> index = new HashMap<>();
    for (SkillNameRow skill : skills) {
      String source = skill.getNormalizedName() != null ? skill.getNormalizedName() : skill.getName();
      put(index, source, new SkillRef(skill.getId(), skill.getName()));
    }
    for (AliasNameRow alias : aliases) {
      if (alias.getSkillName() != null) {
        put(index, alias.getNormalizedAlias(), new SkillRef(alias.getSkillId(), alias.getSkillName()));
      }
    }
    return Collections.unmodifiableMap(index);
  }

  private static void put(Map<String, SkillRef> index, String source, SkillRef ref) {
    String key = SkillUtils.normalizeSkill(source);
    if (key != null && !key.isEmpty()) {
      index.putIfAbsent(key, ref);
    }
  }

  private record CachedIndex(Map<String, SkillRef> index, Instant loadedAt) {
  }
}
