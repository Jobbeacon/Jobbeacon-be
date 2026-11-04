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
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class SkillLookupServiceImpl implements SkillLookupService {

  private static final int MIN_SEARCH_LIMIT = 1;
  private static final int MAX_SEARCH_LIMIT = 20;
  private static final int RANK_EXACT = 0;
  private static final int RANK_PREFIX = 1;
  private static final int RANK_CONTAINS = 2;
  private static final Comparator<RankedSkill> SEARCH_ORDER = Comparator
      .comparingInt(RankedSkill::rank)
      .thenComparingInt(r -> r.ref().name().length())
      .thenComparing(r -> r.ref().name(), String.CASE_INSENSITIVE_ORDER)
      .thenComparing(r -> r.ref().id().toString());

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

  @Override
  public List<SkillRef> search(String q, int limit) {
    String query = SkillUtils.normalizeSkill(q);
    if (query == null || query.isEmpty()) {
      return List.of();
    }
    int size = Math.max(MIN_SEARCH_LIMIT, Math.min(MAX_SEARCH_LIMIT, limit));
    Map<UUID, RankedSkill> best = new HashMap<>();
    for (Map.Entry<String, SkillRef> entry : nameIndex().entrySet()) {
      int rank = rank(entry.getKey(), query);
      if (rank < 0) {
        continue;
      }
      SkillRef ref = entry.getValue();
      best.merge(ref.id(), new RankedSkill(ref, rank),
          (current, candidate) -> candidate.rank() < current.rank() ? candidate : current);
    }
    return best.values().stream()
        .sorted(SEARCH_ORDER)
        .limit(size)
        .map(RankedSkill::ref)
        .toList();
  }

  private static int rank(String key, String query) {
    if (key.equals(query)) {
      return RANK_EXACT;
    }
    if (key.startsWith(query)) {
      return RANK_PREFIX;
    }
    return key.contains(query) ? RANK_CONTAINS : -1;
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

  private record RankedSkill(SkillRef ref, int rank) {
  }

  private record CachedIndex(Map<String, SkillRef> index, Instant loadedAt) {
  }
}
