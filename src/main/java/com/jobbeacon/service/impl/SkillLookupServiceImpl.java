package com.jobbeacon.service.impl;

import com.jobbeacon.dto.skill.SkillRef;
import com.jobbeacon.repository.SkillRepository;
import com.jobbeacon.service.SkillLookupService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class SkillLookupServiceImpl implements SkillLookupService {

  private final SkillRepository skillRepository;
  private final Duration ttl;
  private final Clock clock;
  private final Object reloadLock = new Object();

  private volatile CachedIndex cache;

  @Autowired
  public SkillLookupServiceImpl(
      SkillRepository skillRepository,
      @Value("${app.cv.skill-index-ttl:10m}") Duration ttl
  ) {
    this(skillRepository, ttl, Clock.systemUTC());
  }

  SkillLookupServiceImpl(SkillRepository skillRepository, Duration ttl, Clock clock) {
    this.skillRepository = skillRepository;
    this.ttl = ttl;
    this.clock = clock;
  }

  @Override
  public Optional<SkillRef> findByName(String raw) {
    String name = raw == null ? "" : raw.trim();
    if (name.isEmpty()) {
      return Optional.empty();
    }
    return skillRepository.findFirstByNameIgnoreCaseOrderByIdAsc(name)
        .map(skill -> new SkillRef(skill.getId(), skill.getName()));
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
      Map<String, SkillRef> index = buildIndex(skillRepository.findAllRefsOrderByIdAsc());
      cache = new CachedIndex(index, clock.instant());
      log.info("(nameIndex) loaded skill index: {} names in {} ms", index.size(),
          System.currentTimeMillis() - start);
      return index;
    }
  }

  private boolean isFresh(CachedIndex current) {
    return current != null && clock.instant().isBefore(current.loadedAt().plus(ttl));
  }

  private static Map<String, SkillRef> buildIndex(List<SkillRef> refs) {
    Map<String, SkillRef> index = new HashMap<>();
    for (SkillRef ref : refs) {
      if (ref.name() == null) {
        continue;
      }
      String key = ref.name().trim().toLowerCase(Locale.ROOT);
      if (!key.isEmpty()) {
        index.putIfAbsent(key, ref);
      }
    }
    return Collections.unmodifiableMap(index);
  }

  private record CachedIndex(Map<String, SkillRef> index, Instant loadedAt) {
  }
}
