package com.jobbeacon.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.jobbeacon.exception.base.InternalServerError;
import com.jobbeacon.repository.JobElasticsearchRepository;
import com.jobbeacon.repository.JobElasticsearchRepository.TitleHit;
import com.jobbeacon.repository.JobElasticsearchRepository.TitleQuery;
import com.jobbeacon.repository.TitleMatchRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class TitleJobServiceTest {

  private static final UUID USER = UUID.randomUUID();

  private final InternalRecommendationService titleSource = mock(InternalRecommendationService.class);
  private final JobElasticsearchRepository search = mock(JobElasticsearchRepository.class);
  private final TitleMatchRepository matches = mock(TitleMatchRepository.class);
  private final TitleJobService service = new TitleJobService(titleSource, search, matches);

  @BeforeEach
  void setUp() {
    ReflectionTestUtils.setField(service, "matchByMeaning", true);
  }

  @Test
  void withoutTitlesNothingIsSearched() {
    when(titleSource.titles(USER)).thenReturn(new UserTitles(List.of(), List.of(), List.of()));

    assertThat(service.findJobIds(USER, 50)).isEmpty();
    verifyNoInteractions(search, matches);
  }

  @Test
  void pastTitlesWeighLessAndHitsThatOnlyLookAlikeAreDropped() {
    UUID match = UUID.randomUUID();
    when(titleSource.titles(USER)).thenReturn(new UserTitles(List.of(), List.of("c++ developer"), List.of("tester")));
    when(search.findOpenByTitles(any(), anyInt())).thenReturn(List.of(
        new TitleHit(UUID.randomUUID(), "C Developer"), new TitleHit(match, "Senior C++ Developer")));

    assertThat(service.findJobIds(USER, 50)).containsExactly(match);
    verify(search).findOpenByTitles(List.of(new TitleQuery("c++ developer", 2f), new TitleQuery("tester", 1f)), 50);
  }

  @Test
  void searchBeingDownOnlyCostsTheTitleJobs() {
    when(titleSource.titles(USER)).thenReturn(new UserTitles(List.of("Tester"), List.of(), List.of()));
    when(search.findOpenByTitles(any(), anyInt()))
        .thenThrow(new InternalServerError("search_unavailable", "search_unavailable", null));

    when(matches.findMatchingOpenJobs(List.of("Tester"), 50)).thenReturn(List.of(USER));

    assertThat(service.findJobIds(USER, 50)).containsExactly(USER);
  }

  @Test
  void jobsOfTheSameOccupationFollowThoseWithTheSameWords() {
    UUID both = UUID.randomUUID();
    UUID words = UUID.randomUUID();
    UUID meaning = UUID.randomUUID();
    when(titleSource.titles(USER)).thenReturn(new UserTitles(List.of("Tester"), List.of(), List.of()));
    when(search.findOpenByTitles(any(), anyInt()))
        .thenReturn(List.of(new TitleHit(words, "Tester"), new TitleHit(both, "Game Tester")));
    when(matches.findMatchingOpenJobs(List.of("Tester"), 3)).thenReturn(List.of(both, meaning, UUID.randomUUID()));

    assertThat(service.findJobIds(USER, 3)).containsExactly(words, both, meaning);
    verify(matches).request(List.of("Tester"));
  }

  @Test
  void meaningCanBeTurnedOff() {
    ReflectionTestUtils.setField(service, "matchByMeaning", false);
    when(titleSource.titles(USER)).thenReturn(new UserTitles(List.of("Tester"), List.of(), List.of()));

    assertThat(service.findJobIds(USER, 50)).isEmpty();
    verifyNoInteractions(matches);
  }
}
