package com.jobbeacon.repository;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.Operator;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.json.JsonData;
import com.jobbeacon.dto.request.JobSearchRequest;
import com.jobbeacon.dto.response.JobSummaryResponse;
import com.jobbeacon.dto.response.PageResponse;
import com.jobbeacon.exception.base.InternalServerError;
import com.jobbeacon.search.ElasticsearchProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Repository
@RequiredArgsConstructor
public class JobElasticsearchRepository {

    private final ElasticsearchClient elasticsearchClient;
    private final ElasticsearchProperties properties;

    public PageResponse<JobSummaryResponse> search(JobSearchRequest request) {
        int page = Math.max(request.getPage() - 1, 0);
        int size = request.getSize() > 0 ? request.getSize() : 10;
        String keyword = text(request.getKeyword());
        String sortBy = sortField(request.getSortBy(), keyword);

        try {
            SearchResponse<JsonData> response = elasticsearchClient.search(search -> {
                search.index(properties.getIndex())
                        .from(page * size)
                        .size(size)
                        .trackTotalHits(total -> total.enabled(true))
                        .query(query -> query.bool(bool -> applyFilters(bool, request, keyword)));
                if ("max_salary".equals(sortBy)) {
                    search.sort(sort -> sort.field(field -> field.field("max_salary").order(SortOrder.Desc)));
                } else if (!"relevance".equals(sortBy)) {
                    search.sort(sort -> sort.field(field -> field.field("posted_date").order(SortOrder.Desc)));
                }
                return search;
            }, JsonData.class);
            List<JobSummaryResponse> content = new ArrayList<>();
            for (Hit<JsonData> hit : response.hits().hits()) {
                if (hit.source() == null) {
                    continue;
                }
                content.add(toSummary(hit.id(), hit.source().to(Map.class)));
            }
            long total = response.hits().total() == null ? content.size() : response.hits().total().value();
            return PageResponse.of(content, (int) Math.min(total, Integer.MAX_VALUE));
        } catch (IOException | ElasticsearchException exception) {
            log.error("Job search failed because Elasticsearch is unavailable", exception);
            throw new InternalServerError("search_unavailable", "search_unavailable", null);
        }
    }

    private JobSummaryResponse toSummary(String hitId, Map<String, Object> source) {
        Object id = source.get("id");
        List<String> locations = source.get("locations") instanceof List<?> values
                ? values.stream().map(String::valueOf).toList()
                : List.of();
        return new JobSummaryResponse(
                id == null ? UUID.fromString(hitId) : UUID.fromString(String.valueOf(id)),
                text(source.get("title")),
                text(source.get("company_name")),
                longValue(source.get("min_salary")),
                longValue(source.get("max_salary")),
                locations,
                text(source.get("currency")),
                text(source.get("company_logo_url")),
                instant(source.get("posted_date"))
        );
    }

    private BoolQuery.Builder applyFilters(BoolQuery.Builder bool, JobSearchRequest request, String keyword) {
        if (keyword.isEmpty()) {
            bool.must(must -> must.matchAll(match -> match));
        } else {
            bool.must(must -> must.multiMatch(match -> match
                    .query(keyword)
                    .fields("title^3", "company_name^2", "skills.name^2", "description", "locations")
                    .operator(Operator.And)));
        }

        List<String> places = new ArrayList<>(split(request.getLocations()));
        places.addAll(split(request.getLocation(), "\\|"));
        filterLocations(bool, distinct(places));
        filterFieldMatches(bool, "categories.name", split(request.getCategories()));
        filterFieldMatches(bool, "skills.name", split(request.getSkills()));
        filterTerms(bool, "employment_type", split(request.getEmploymentTypes()));
        filterTerms(bool, "sources.domain", split(request.getSources()));
        filterExperience(bool, split(request.getExperience()));
        filterPostedWithin(bool, split(request.getPostedWithin()));
        filterSalary(bool, request.getMinSalary(), request.getMaxSalary());
        return bool;
    }

    private void filterLocations(BoolQuery.Builder bool, List<String> places) {
        if (places.isEmpty()) {
            return;
        }
        bool.filter(filter -> filter.bool(inner -> {
            inner.minimumShouldMatch("1");
            for (String place : places) {
                inner.should(should -> should.multiMatch(match -> match
                        .query(place)
                        .fields("locations", "province_names")
                        .type(TextQueryType.CrossFields)
                        .operator(Operator.And)));
            }
            return inner;
        }));
    }

    private void filterFieldMatches(BoolQuery.Builder bool, String field, List<String> values) {
        if (values.isEmpty()) {
            return;
        }
        bool.filter(filter -> filter.bool(inner -> {
            inner.minimumShouldMatch("1");
            for (String value : values) {
                inner.should(should -> should.match(match -> match
                        .field(field)
                        .query(value)
                        .operator(Operator.And)));
            }
            return inner;
        }));
    }

    private void filterTerms(BoolQuery.Builder bool, String field, List<String> values) {
        if (values.isEmpty()) {
            return;
        }
        bool.filter(filter -> filter.bool(inner -> {
            inner.minimumShouldMatch("1");
            for (String value : values) {
                inner.should(should -> should.term(term -> term.field(field).value(value)));
            }
            return inner;
        }));
    }

    private void filterExperience(BoolQuery.Builder bool, List<String> values) {
        List<String> known = values.stream()
                .filter(value -> value.equals("none") || value.equals("y1_3") || value.equals("y3_5") || value.equals("y5"))
                .toList();
        if (known.isEmpty()) {
            return;
        }
        bool.filter(filter -> filter.bool(inner -> {
            inner.minimumShouldMatch("1");
            for (String value : known) {
                switch (value) {
                    case "none" -> inner.should(should -> should.range(range -> range
                            .number(number -> number.field("experience_months").lte(0.0))));
                    case "y1_3" -> inner.should(should -> should.range(range -> range
                            .number(number -> number.field("experience_months").gte(12.0).lte(36.0))));
                    case "y3_5" -> inner.should(should -> should.range(range -> range
                            .number(number -> number.field("experience_months").gte(36.0).lte(60.0))));
                    case "y5" -> inner.should(should -> should.range(range -> range
                            .number(number -> number.field("experience_months").gte(60.0))));
                    default -> {
                    }
                }
            }
            return inner;
        }));
    }

    private void filterPostedWithin(BoolQuery.Builder bool, List<String> values) {
        String since = null;
        if (values.contains("30d")) {
            since = "now-30d";
        } else if (values.contains("7d")) {
            since = "now-7d";
        } else if (values.contains("24h")) {
            since = "now-24h";
        }
        if (since == null) {
            return;
        }
        String postedSince = since;
        bool.filter(filter -> filter.range(range -> range.date(date -> date.field("posted_date").gte(postedSince))));
    }

    private void filterSalary(BoolQuery.Builder bool, Long minSalary, Long maxSalary) {
        if (minSalary != null) {
            double min = minSalary.doubleValue();
            bool.filter(filter -> filter.bool(inner -> inner
                    .minimumShouldMatch("1")
                    .should(should -> should.range(range -> range.number(number -> number.field("max_salary").gte(min))))
                    .should(should -> should.range(range -> range.number(number -> number.field("min_salary").gte(min))))));
        }
        if (maxSalary != null) {
            double max = maxSalary.doubleValue();
            bool.filter(filter -> filter.bool(inner -> inner
                    .minimumShouldMatch("1")
                    .should(should -> should.range(range -> range.number(number -> number.field("min_salary").lte(max))))
                    .should(should -> should.range(range -> range.number(number -> number.field("max_salary").lte(max))))));
        }
    }

    private String sortField(String sortBy, String keyword) {
        if (sortBy == null || sortBy.isBlank() || "posted_date".equals(sortBy)) {
            return "posted_date";
        }
        if ("relevance".equals(sortBy)) {
            return keyword.isEmpty() ? "posted_date" : "relevance";
        }
        if ("max_salary".equals(sortBy)) {
            return "max_salary";
        }
        return "posted_date";
    }

    private List<String> split(String value) {
        return split(value, ",");
    }

    private List<String> split(String value, String separator) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split(separator))
                .map(String::trim)
                .filter(item -> !item.isEmpty())
                .toList();
    }

    private List<String> distinct(List<String> values) {
        Set<String> unique = new LinkedHashSet<>();
        for (String value : values) {
            if (!value.isBlank()) {
                unique.add(value);
            }
        }
        return List.copyOf(unique);
    }

    private String text(String value) {
        return value == null ? "" : value.trim();
    }

    private String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return null;
        }
        return Long.valueOf(String.valueOf(value));
    }

    private Instant instant(Object value) {
        if (value == null) {
            return null;
        }
        return Instant.parse(String.valueOf(value));
    }
}
