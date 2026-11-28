package com.jobbeacon.repository;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.mapping.RuntimeField;
import co.elastic.clients.elasticsearch._types.mapping.RuntimeFieldType;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregate;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.aggregations.AggregationRange;
import co.elastic.clients.elasticsearch._types.aggregations.CalendarInterval;
import co.elastic.clients.elasticsearch._types.aggregations.Percentiles;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.json.JsonData;
import com.jobbeacon.exception.base.InternalServerError;
import com.jobbeacon.search.ElasticsearchProperties;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

/**
 * Market statistics straight from the {@code jobs} index, in one search with aggregations. Salaries come from a
 * runtime field, so the index needs no extra field and no rebuild.
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class MarketElasticsearchRepository {

    /** Salary per job, VND per month (see {@link #SALARY_SCRIPT}). */
    static final String SALARY = "salary_month_vnd";
    /** Monthly salaries outside this range are typos or a wrong period, and are left out. */
    static final long MIN_MONTHLY_SALARY = 2_000_000L;
    static final long MAX_MONTHLY_SALARY = 300_000_000L;
    static final int TOP_SKILLS = 15;
    static final int TOP_PROVINCES = 10;
    static final int TREND_WEEKS = 26;

    /**
     * Middle of the salary range (or the one end given), converted to VND and to a month: 22 working days, 176 hours.
     * Jobs without a salary, or 0 for "negotiable", emit nothing.
     */
    private static final String SALARY_SCRIPT = """
            double lo = doc['min_salary'].size() == 0 ? 0 : doc['min_salary'].value;
            double hi = doc['max_salary'].size() == 0 ? 0 : doc['max_salary'].value;
            if (lo <= 0 && hi <= 0) { return; }
            double mid = lo > 0 && hi > 0 ? (lo + hi) / 2.0 : Math.max(lo, hi);
            if (doc['currency'].size() > 0 && doc['currency'].value == 'USD') { mid *= params.usd; }
            String period = doc['salary_period'].size() > 0 ? doc['salary_period'].value : 'MONTH';
            if (period == 'YEAR') { mid /= 12; } else if (period == 'DAY') { mid *= 22; } else if (period == 'HOUR') { mid *= 176; }
            if (mid >= params.min && mid <= params.max) { emit(mid); }
            """;

    /**
     * Experience buckets, in months; the upper bound is exclusive. 0 gets its own bucket: crawled jobs that state no
     * experience are stored as 0 too, so it means "none required or not stated", not "under a year".
     */
    private static final List<AggregationRange> EXPERIENCE_RANGES = List.of(
            AggregationRange.of(range -> range.key("none").to(1.0)),
            AggregationRange.of(range -> range.key("lt_1y").from(1.0).to(12.0)),
            AggregationRange.of(range -> range.key("y1_3").from(12.0).to(36.0)),
            AggregationRange.of(range -> range.key("y3_5").from(36.0).to(60.0)),
            AggregationRange.of(range -> range.key("y5").from(60.0)));

    private final ElasticsearchClient elasticsearchClient;
    private final ElasticsearchProperties properties;

    /**
     * @param categoryIds jobs in any of these categories; null for no category filter
     * @param provinceId  jobs in this province (its code, e.g. "01"); null for any
     * @param skillId     jobs that need this skill; null for any
     */
    public record Filter(Collection<String> categoryIds, String provinceId, String skillId) {
    }

    public record Salary(Long p25, Long median, Long p75, long sampleSize) {
    }

    public record Group(String key, long jobCount, Salary salary) {
    }

    public record Week(LocalDate start, long jobCount) {
    }

    public record Overview(
            long totalJobs,
            Salary salary,
            List<Group> skills,
            List<Group> provinces,
            List<Group> categories,
            List<Group> experience,
            List<Week> trend) {
    }

    /**
     * @param categoryGroups category groups to break the jobs down by: group key to the category ids it covers
     * @param usdVndRate     VND per USD
     */
    public Overview overview(Filter filter, Map<String, List<String>> categoryGroups, double usdVndRate) {
        Map<String, Aggregation> aggregations = new LinkedHashMap<>(salaryAggregations());
        aggregations.put("skills", grouped(Aggregation.of(a -> a.terms(t -> t.field("skills.id").size(TOP_SKILLS)))));
        aggregations.put("provinces", grouped(Aggregation.of(a -> a.terms(t -> t.field("province_ids").size(TOP_PROVINCES)))));
        if (!categoryGroups.isEmpty()) {
            Map<String, Query> byGroup = new LinkedHashMap<>();
            categoryGroups.forEach((key, ids) -> byGroup.put(key, anyOf("categories.id", ids)));
            aggregations.put("categories", grouped(Aggregation.of(a -> a.filters(f -> f.filters(b -> b.keyed(byGroup))))));
        }
        aggregations.put("experience", grouped(Aggregation.of(a -> a.range(r -> r
                .field("experience_months").ranges(EXPERIENCE_RANGES)))));
        aggregations.put("trend", Aggregation.of(a -> a
                .filter(f -> f.range(r -> r.date(d -> d.field("posted_date").gte("now-" + TREND_WEEKS + "w/w"))))
                .aggregations("weeks", Aggregation.of(w -> w.dateHistogram(h -> h
                        .field("posted_date").calendarInterval(CalendarInterval.Week).minDocCount(0))))));

        try {
            SearchResponse<Void> response = elasticsearchClient.search(search -> search
                    .index(properties.getIndex())
                    .size(0)
                    .trackTotalHits(total -> total.enabled(true))
                    .runtimeMappings(SALARY, salaryField(usdVndRate))
                    .query(query -> query.bool(bool -> applyFilter(bool, filter)))
                    .aggregations(aggregations), Void.class);
            Map<String, Aggregate> result = response.aggregations();
            return new Overview(
                    response.hits().total() == null ? 0 : response.hits().total().value(),
                    salary(result),
                    termGroups(result.get("skills")),
                    termGroups(result.get("provinces")),
                    categoryGroups.isEmpty() ? List.of() : filterGroups(result.get("categories")),
                    rangeGroups(result.get("experience")),
                    weeks(result.get("trend")));
        } catch (IOException | ElasticsearchException exception) {
            log.error("Market statistics failed because Elasticsearch is unavailable", exception);
            throw new InternalServerError("search_unavailable", "search_unavailable", null);
        }
    }

    private static RuntimeField salaryField(double usdVndRate) {
        return RuntimeField.of(field -> field
                .type(RuntimeFieldType.Double)
                .script(script -> script
                        .source(SALARY_SCRIPT)
                        .params("usd", JsonData.of(usdVndRate))
                        .params("min", JsonData.of(MIN_MONTHLY_SALARY))
                        .params("max", JsonData.of(MAX_MONTHLY_SALARY))));
    }

    private static BoolQuery.Builder applyFilter(BoolQuery.Builder bool, Filter filter) {
        bool.must(must -> must.matchAll(all -> all));
        if (filter.categoryIds() != null) {
            bool.filter(anyOf("categories.id", filter.categoryIds()));
        }
        if (filter.provinceId() != null) {
            bool.filter(term -> term.term(t -> t.field("province_ids").value(filter.provinceId())));
        }
        if (filter.skillId() != null) {
            bool.filter(term -> term.term(t -> t.field("skills.id").value(filter.skillId())));
        }
        return bool;
    }

    /** Matches documents with any of {@code values} in {@code field}, and none at all when the list is empty. */
    private static Query anyOf(String field, Collection<String> values) {
        if (values.isEmpty()) {
            return Query.of(q -> q.bool(b -> b.mustNot(not -> not.matchAll(all -> all))));
        }
        List<FieldValue> terms = values.stream().map(FieldValue::of).toList();
        return Query.of(q -> q.terms(t -> t.field(field).terms(v -> v.value(terms))));
    }

    private static Map<String, Aggregation> salaryAggregations() {
        Map<String, Aggregation> salary = new LinkedHashMap<>();
        salary.put("salary", Aggregation.of(a -> a.percentiles(p -> p.field(SALARY).percents(25.0, 50.0, 75.0))));
        salary.put("salary_count", Aggregation.of(a -> a.valueCount(v -> v.field(SALARY))));
        return salary;
    }

    /** Adds the salary figures under every bucket of {@code buckets}. */
    private static Aggregation grouped(Aggregation buckets) {
        return Aggregation.of(a -> {
            var builder = switch (buckets._kind()) {
                case Terms -> a.terms(buckets.terms());
                case Filters -> a.filters(buckets.filters());
                case Range -> a.range(buckets.range());
                default -> throw new IllegalArgumentException("Unsupported grouping " + buckets._kind());
            };
            return builder.aggregations(salaryAggregations());
        });
    }

    private static Salary salary(Map<String, Aggregate> aggregations) {
        Map<String, String> percentiles = keyed(aggregations.get("salary").tdigestPercentiles().values());
        return new Salary(
                money(percentiles.get("25.0")),
                money(percentiles.get("50.0")),
                money(percentiles.get("75.0")),
                (long) aggregations.get("salary_count").valueCount().value());
    }

    private static Map<String, String> keyed(Percentiles values) {
        return values.isKeyed() ? values.keyed() : Map.of();
    }

    /** Percentiles come back as strings; an empty group gives null or "NaN". */
    private static Long money(String value) {
        if (value == null) {
            return null;
        }
        try {
            double number = Double.parseDouble(value);
            return Double.isNaN(number) ? null : Math.round(number);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static List<Group> termGroups(Aggregate aggregate) {
        List<Group> groups = new ArrayList<>();
        aggregate.sterms().buckets().array().forEach(bucket ->
                groups.add(new Group(bucket.key().stringValue(), bucket.docCount(), salary(bucket.aggregations()))));
        return groups;
    }

    private static List<Group> filterGroups(Aggregate aggregate) {
        List<Group> groups = new ArrayList<>();
        aggregate.filters().buckets().keyed().forEach((key, bucket) ->
                groups.add(new Group(key, bucket.docCount(), salary(bucket.aggregations()))));
        return groups;
    }

    private static List<Group> rangeGroups(Aggregate aggregate) {
        List<Group> groups = new ArrayList<>();
        aggregate.range().buckets().array().forEach(bucket ->
                groups.add(new Group(bucket.key(), bucket.docCount(), salary(bucket.aggregations()))));
        return groups;
    }

    private static List<Week> weeks(Aggregate aggregate) {
        List<Week> weeks = new ArrayList<>();
        aggregate.filter().aggregations().get("weeks").dateHistogram().buckets().array().forEach(bucket ->
                weeks.add(new Week(
                        LocalDate.ofInstant(Instant.ofEpochMilli(bucket.key()), ZoneOffset.UTC),
                        bucket.docCount())));
        return weeks;
    }
}
