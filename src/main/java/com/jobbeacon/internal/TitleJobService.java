package com.jobbeacon.internal;

import com.jobbeacon.repository.JobElasticsearchRepository;
import com.jobbeacon.repository.JobElasticsearchRepository.TitleHit;
import com.jobbeacon.repository.JobElasticsearchRepository.TitleQuery;
import com.jobbeacon.repository.TitleMatchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Finds open jobs by the titles a user looks for, so that the recommendation service also considers jobs its graph does
 * not lead to. Deliberately not transactional: no database connection is held while Elasticsearch answers.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TitleJobService {

    private static final float CURRENT_BOOST = 2f;
    private static final float PAST_BOOST = 1f;

    private final InternalRecommendationService internalRecommendationService;
    private final JobElasticsearchRepository jobElasticsearchRepository;
    private final TitleMatchRepository titleMatchRepository;

    @Value("${app.title-matching.enabled}")
    private boolean matchByMeaning;

    /**
     * Jobs with the words of a title first, then those of the same occupation. The latter are worked out in the
     * background ({@link TitleMatchWorker}), so for a title nobody has used before they only turn up on a later call.
     */
    public List<UUID> findJobIds(UUID userId, int limit) {
        UserTitles titles = internalRecommendationService.titles(userId);
        if (titles.isEmpty() || limit < 1) {
            return List.of();
        }
        Set<UUID> jobIds = new LinkedHashSet<>(findByWords(titles, limit));
        if (matchByMeaning) {
            titleMatchRepository.request(titles.all());
            jobIds.addAll(titleMatchRepository.findMatchingOpenJobs(titles.all(), limit));
        }
        return jobIds.stream().limit(limit).toList();
    }

    /** Best match first, then newest. Empty when the search is down. */
    private List<UUID> findByWords(UserTitles titles, int limit) {
        List<TitleQuery> queries = Stream.of(
                queries(titles.desired(), CURRENT_BOOST),
                queries(titles.current(), CURRENT_BOOST),
                queries(titles.past(), PAST_BOOST))
            .flatMap(stream -> stream)
            .toList();
        try {
            // The index splits words differently ("C++" becomes "c"), so its hits are checked with the same rule that
            // scores them later.
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
