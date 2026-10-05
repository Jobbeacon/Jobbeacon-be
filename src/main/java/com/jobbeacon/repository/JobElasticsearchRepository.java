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

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
        String keyword = request.getKeyword() == null ? "" : request.getKeyword().trim();

        try {
            SearchResponse<JsonData> response = elasticsearchClient.search(search -> search
                            .index(properties.getIndex())
                            .from(page * size)
                            .size(size)
                            .trackTotalHits(total -> total.enabled(true))
                            .sort(sort -> sort.field(field -> field.field("posted_date").order(SortOrder.Desc)))
                            .query(query -> keyword.isEmpty()
                                    ? query.matchAll(match -> match)
                                    : query.multiMatch(match -> match
                                    .query(keyword)
                                    .fields("title^3", "company_name^2", "skills.name^2", "description", "locations")
                                    .operator(Operator.And))),
                    JsonData.class);
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
