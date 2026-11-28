package com.jobbeacon.service;

import com.jobbeacon.configuration.MarketProperties;
import com.jobbeacon.dto.response.market.MarketOverviewResponse;
import com.jobbeacon.dto.response.market.MarketOverviewResponse.Group;
import com.jobbeacon.dto.response.market.MarketOverviewResponse.SalaryStats;
import com.jobbeacon.dto.response.market.MarketOverviewResponse.TrendPoint;
import com.jobbeacon.entity.Province;
import com.jobbeacon.entity.Skill;
import com.jobbeacon.repository.MarketElasticsearchRepository;
import com.jobbeacon.repository.MarketElasticsearchRepository.Filter;
import com.jobbeacon.repository.MarketElasticsearchRepository.Overview;
import com.jobbeacon.repository.MarketElasticsearchRepository.Salary;
import com.jobbeacon.repository.ProvinceRepository;
import com.jobbeacon.repository.SkillRepository;
import jakarta.persistence.EntityManager;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Market statistics (salaries, skills in demand, postings over time) over the crawled jobs. The figures come from
 * Elasticsearch; names come from the database. Categories are grouped by their root, each root covering its whole
 * subtree.
 */
@Service
@RequiredArgsConstructor
public class MarketInsightService {

    /** The category tree changes only through the admin pages, so it is reloaded at most this often. */
    private static final Duration CATEGORY_TREE_TTL = Duration.ofMinutes(10);

    private static final Map<String, String> EXPERIENCE_LABELS = Map.of(
            "none", "Không yêu cầu hoặc không ghi",
            "lt_1y", "Dưới 1 năm",
            "y1_3", "1–3 năm",
            "y3_5", "3–5 năm",
            "y5", "Trên 5 năm");

    private final MarketElasticsearchRepository marketRepository;
    private final SkillRepository skillRepository;
    private final ProvinceRepository provinceRepository;
    private final EntityManager entityManager;
    private final MarketProperties properties;

    private volatile CategoryTree categoryTree;

    /**
     * @param categoryId any category: the jobs in it or under it; null for all
     * @param provinceId province code, e.g. "01"; null for all
     * @param skillId    null for all
     */
    @Transactional(readOnly = true)
    public MarketOverviewResponse overview(UUID categoryId, String provinceId, UUID skillId) {
        CategoryTree tree = categoryTree();
        Filter filter = new Filter(
                categoryId == null ? null : tree.subtree(categoryId.toString()),
                blankToNull(provinceId),
                skillId == null ? null : skillId.toString());
        Overview overview = marketRepository.overview(filter, tree.rootSubtrees(), properties.usdVndRate());
        long total = overview.totalJobs();

        Map<String, String> skillNames = skillRepository.findAllById(ids(overview.skills())).stream()
                .collect(Collectors.toMap(skill -> skill.getId().toString(), Skill::getName));
        Map<String, String> provinceNames = provinceRepository
                .findAllById(overview.provinces().stream().map(MarketElasticsearchRepository.Group::key).toList())
                .stream()
                .collect(Collectors.toMap(Province::getId, Province::getNameLocal));

        return new MarketOverviewResponse(
                total,
                overview.salary().sampleSize(),
                salary(overview.salary()),
                groups(overview.skills(), skillNames::get, total),
                groups(overview.provinces(), provinceNames::get, total),
                groups(overview.categories(), tree.names()::get, total).stream()
                        .filter(group -> group.jobCount() > 0)
                        .sorted(Comparator.comparingLong(Group::jobCount).reversed())
                        .toList(),
                groups(overview.experience(), EXPERIENCE_LABELS::get, total),
                overview.trend().stream().map(week -> new TrendPoint(week.start(), week.jobCount())).toList(),
                properties.usdVndRate(),
                properties.minSalarySample());
    }

    /** Groups whose name is unknown (e.g. a skill deleted since the job was indexed) are left out. */
    private List<Group> groups(List<MarketElasticsearchRepository.Group> raw, Function<String, String> name, long total) {
        List<Group> groups = new ArrayList<>();
        for (MarketElasticsearchRepository.Group group : raw) {
            String label = name.apply(group.key());
            if (label != null) {
                groups.add(new Group(group.key(), label, group.jobCount(),
                        total == 0 ? 0 : (double) group.jobCount() / total, salary(group.salary())));
            }
        }
        return groups;
    }

    /** A median of three salaries says little, so small groups report only how many salaries they have. */
    private SalaryStats salary(Salary salary) {
        if (salary.sampleSize() < properties.minSalarySample()) {
            return new SalaryStats(null, null, null, salary.sampleSize());
        }
        return new SalaryStats(salary.p25(), salary.median(), salary.p75(), salary.sampleSize());
    }

    private static List<UUID> ids(List<MarketElasticsearchRepository.Group> groups) {
        List<UUID> ids = new ArrayList<>();
        for (MarketElasticsearchRepository.Group group : groups) {
            try {
                ids.add(UUID.fromString(group.key()));
            } catch (IllegalArgumentException ignored) {
                // Not a skill id: nothing to look up.
            }
        }
        return ids;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private CategoryTree categoryTree() {
        CategoryTree tree = categoryTree;
        if (tree == null || tree.loadedAt().plus(CATEGORY_TREE_TTL).isBefore(Instant.now())) {
            tree = loadCategoryTree();
            categoryTree = tree;
        }
        return tree;
    }

    @SuppressWarnings("unchecked")
    private CategoryTree loadCategoryTree() {
        List<Object[]> rows = entityManager
                .createNativeQuery("select cast(id as varchar), cast(parent_id as varchar), name from category")
                .getResultList();
        Map<String, String> names = new HashMap<>();
        Map<String, List<String>> children = new HashMap<>();
        List<String> roots = new ArrayList<>();
        for (Object[] row : rows) {
            String id = (String) row[0];
            String parent = (String) row[1];
            names.put(id, (String) row[2]);
            if (parent == null) {
                roots.add(id);
            } else {
                children.computeIfAbsent(parent, ignored -> new ArrayList<>()).add(id);
            }
        }
        roots.sort(Comparator.comparing(names::get));
        Map<String, List<String>> rootSubtrees = new LinkedHashMap<>();
        for (String root : roots) {
            rootSubtrees.put(root, subtree(root, children));
        }
        return new CategoryTree(names, children, rootSubtrees, Instant.now());
    }

    private static List<String> subtree(String root, Map<String, List<String>> children) {
        List<String> ids = new ArrayList<>();
        Deque<String> pending = new ArrayDeque<>(List.of(root));
        while (!pending.isEmpty()) {
            String id = pending.pop();
            ids.add(id);
            pending.addAll(children.getOrDefault(id, List.of()));
        }
        return ids;
    }

    private record CategoryTree(
            Map<String, String> names,
            Map<String, List<String>> children,
            Map<String, List<String>> rootSubtrees,
            Instant loadedAt) {

        /** The category and everything under it; empty for an unknown id, so the filter matches nothing. */
        List<String> subtree(String id) {
            return names.containsKey(id) ? MarketInsightService.subtree(id, children) : List.of();
        }
    }
}
