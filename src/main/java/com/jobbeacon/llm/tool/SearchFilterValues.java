package com.jobbeacon.llm.tool;

import com.jobbeacon.constant.EmploymentType;
import com.jobbeacon.repository.CategoryRepository;
import com.jobbeacon.repository.JobSourceRepository;
import com.jobbeacon.repository.ProvinceRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.text.Collator;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Allowed values for the {@code search_jobs} filters. DB-backed lists are loaded lazily and
 * refreshed after {@code app.chat.filter-values-refresh}; a failed load is not cached.
 */
@Slf4j
@Component
public class SearchFilterValues {

    public static final int ROOT_CATEGORY_LEVEL = 1;

    public static final List<String> EMPLOYMENT_TYPES = List.of(
            EmploymentType.FULL_TIME.name(),
            EmploymentType.PART_TIME.name(),
            EmploymentType.INTERN.name());

    public static final String POSTED_WITHIN_24H = "24h";
    public static final String POSTED_WITHIN_7D = "7d";
    public static final String POSTED_WITHIN_30D = "30d";
    public static final List<String> POSTED_WITHIN = List.of(POSTED_WITHIN_24H, POSTED_WITHIN_7D, POSTED_WITHIN_30D);

    public static final String SORT_RELEVANCE = "relevance";
    public static final String SORT_POSTED_DATE = "posted_date";
    public static final String SORT_MAX_SALARY = "max_salary";
    public static final List<String> SORT_BY = List.of(SORT_RELEVANCE, SORT_POSTED_DATE, SORT_MAX_SALARY);

    private static final Snapshot EMPTY = new Snapshot(List.of(), List.of(), List.of(), 0L);

    private final ProvinceRepository provinceRepository;
    private final CategoryRepository categoryRepository;
    private final JobSourceRepository jobSourceRepository;
    private final long refreshNanos;

    private volatile Snapshot snapshot = EMPTY;

    public SearchFilterValues(ProvinceRepository provinceRepository,
                              CategoryRepository categoryRepository,
                              JobSourceRepository jobSourceRepository,
                              @Value("${app.chat.filter-values-refresh:PT10M}") Duration refresh) {
        this.provinceRepository = provinceRepository;
        this.categoryRepository = categoryRepository;
        this.jobSourceRepository = jobSourceRepository;
        this.refreshNanos = refresh.toNanos();
    }

    /** Province {@code name_local} values, sorted (Vietnamese collation). */
    public List<String> provinces() {
        return current().provinces();
    }

    /** Names of level-1 categories (their path equals their name), sorted. */
    public List<String> rootCategories() {
        return current().rootCategories();
    }

    /** Job source domains, sorted. */
    public List<String> sources() {
        return current().sources();
    }

    public List<String> employmentTypes() {
        return EMPLOYMENT_TYPES;
    }

    public List<String> postedWithin() {
        return POSTED_WITHIN;
    }

    public List<String> sortBy() {
        return SORT_BY;
    }

    public Optional<String> matchProvince(String value) {
        return match(provinces(), value);
    }

    public Optional<String> matchRootCategory(String value) {
        return match(rootCategories(), value);
    }

    public Optional<String> matchSource(String value) {
        return match(sources(), value);
    }

    public Optional<String> matchEmploymentType(String value) {
        return match(EMPLOYMENT_TYPES, value);
    }

    public Optional<String> matchPostedWithin(String value) {
        return match(POSTED_WITHIN, value);
    }

    public Optional<String> matchSortBy(String value) {
        return match(SORT_BY, value);
    }

    /** Returns the canonical allowed value equal to {@code value} ignoring case and surrounding spaces. */
    public static Optional<String> match(Collection<String> allowed, String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        String trimmed = value.trim();
        return allowed.stream().filter(v -> v.equalsIgnoreCase(trimmed)).findFirst();
    }

    /** Drops the cached DB values so the next call reloads them. */
    public void invalidate() {
        snapshot = EMPTY;
    }

    private Snapshot current() {
        Snapshot s = snapshot;
        if (s != EMPTY && System.nanoTime() - s.loadedAt() < refreshNanos) {
            return s;
        }
        synchronized (this) {
            s = snapshot;
            if (s != EMPTY && System.nanoTime() - s.loadedAt() < refreshNanos) {
                return s;
            }
            try {
                Snapshot loaded = load();
                snapshot = loaded;
                log.info("(current) loaded filter values: provinces={}, rootCategories={}, sources={}",
                        loaded.provinces().size(), loaded.rootCategories().size(), loaded.sources().size());
                return loaded;
            } catch (RuntimeException e) {
                log.warn("(current) failed to load filter values: {}", e.getMessage());
                return s;
            }
        }
    }

    private Snapshot load() {
        return new Snapshot(
                sorted(provinceRepository.findAllNameLocals()),
                sorted(categoryRepository.findNamesByLevel(ROOT_CATEGORY_LEVEL)),
                sorted(jobSourceRepository.findAllDomains()),
                System.nanoTime());
    }

    private static List<String> sorted(List<String> values) {
        Collator collator = Collator.getInstance(Locale.forLanguageTag("vi"));
        return values.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(v -> !v.isEmpty())
                .distinct()
                .sorted(collator)
                .toList();
    }

    private record Snapshot(List<String> provinces, List<String> rootCategories, List<String> sources,
                            long loadedAt) {
    }
}
