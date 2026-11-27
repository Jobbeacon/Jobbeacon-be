package com.jobbeacon.internal;

import com.jobbeacon.repository.JobElasticsearchRepository;
import com.jobbeacon.repository.JobElasticsearchRepository.TitleHit;
import com.jobbeacon.repository.JobElasticsearchRepository.TitleQuery;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Finds open jobs by the titles a user looks for, word by word, so that the recommendation service also considers jobs
 * its graph does not lead to. Deliberately not transactional: no database connection is held while Elasticsearch answers.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TitleJobService {

    private static final float CURRENT_BOOST = 2f;
    private static final float PAST_BOOST = 1f;

    private final InternalRecommendationService internalRecommendationService;
    private final JobElasticsearchRepository jobElasticsearchRepository;

    /**
     * Jobs with every word of one of the titles, those the user looks for now before those from earlier jobs, then
     * newest. Empty when the search is down: the recommendation does without.
     */
    public List<UUID> findJobIds(UUID userId, int limit) {
        UserTitles titles = internalRecommendationService.titles(userId);
        if (titles.isEmpty() || limit < 1) {
            return List.of();
        }
        List<TitleQuery> queries = Stream.of(
                queries(titles.desired(), CURRENT_BOOST),
                queries(titles.current(), CURRENT_BOOST),
                queries(titles.past(), PAST_BOOST))
            .flatMap(stream -> stream)
            .toList();
        try {
            // The index splits words differently ("C++" becomes "c"), so its hits are checked with the rule of UserTitles.
            return jobElasticsearchRepository.findOpenByTitles(queries, limit).stream()
                .filter(hit -> UserTitles.anyMatches(titles.all(), hit.title()))
                .map(TitleHit::jobId)
                .toList();
        } catch (RuntimeException e) {
            log.warn("Searching jobs by title failed, going on without them", e);
            return List.of();
        }
    }

    private static Stream<TitleQuery> queries(List<String> titles, float boost) {
        return titles.stream().map(title -> new TitleQuery(title, boost));
    }
}
