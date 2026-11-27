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
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TitleJobServiceTest {

  private static final UUID USER = UUID.randomUUID();

  private final InternalRecommendationService titleSource = mock(InternalRecommendationService.class);
  private final JobElasticsearchRepository search = mock(JobElasticsearchRepository.class);
  private final TitleJobService service = new TitleJobService(titleSource, search);

  @Test
  void withoutTitlesNothingIsSearched() {
    when(titleSource.titles(USER)).thenReturn(new UserTitles(List.of(), List.of(), List.of()));

    assertThat(service.findJobIds(USER, 50)).isEmpty();
    verifyNoInteractions(search);
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

    assertThat(service.findJobIds(USER, 50)).isEmpty();
  }

  @Test
  void aLimitOfZeroSkipsTheSearch() {
    when(titleSource.titles(USER)).thenReturn(new UserTitles(List.of("Tester"), List.of(), List.of()));

    assertThat(service.findJobIds(USER, 0)).isEmpty();
    verifyNoInteractions(search);
  }
}
