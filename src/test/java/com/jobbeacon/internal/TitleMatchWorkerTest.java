package com.jobbeacon.internal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobbeacon.client.GeminiChatClient;
import com.jobbeacon.client.GeminiEmbeddingClient;
import com.jobbeacon.repository.TitleMatchRepository;
import com.jobbeacon.repository.TitleMatchRepository.Title;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class TitleMatchWorkerTest {

  private static final float[] VECTOR = {1f};
  private static final Title TESTER = new Title("tester", "Tester");
  private static final Title SALE = new Title("sale", "Sale");

  private final TitleMatchRepository repository = mock(TitleMatchRepository.class);
  private final GeminiEmbeddingClient embeddings = mock(GeminiEmbeddingClient.class);
  private final GeminiChatClient chat = mock(GeminiChatClient.class);
  private final TitleMatchWorker worker = new TitleMatchWorker(repository, embeddings, chat, new ObjectMapper());

  @BeforeEach
  void setUp() {
    ReflectionTestUtils.setField(worker, "batchSize", 50);
    ReflectionTestUtils.setField(worker, "nearest", 80);
    ReflectionTestUtils.setField(worker, "minSimilarity", 0.55);
    ReflectionTestUtils.setField(worker, "model", "model");
  }

  @Test
  void embedsNewTitlesIncludingThoseOfJobs() {
    when(repository.findWithoutEmbedding(anyInt(), anyInt())).thenReturn(List.of(TESTER, SALE));
    when(embeddings.embed(any(), any())).thenReturn(VECTOR);

    worker.run();

    verify(repository).addOpenJobTitles();
    verify(repository).saveEmbedding("tester", VECTOR, "model");
    verify(repository).saveEmbedding("sale", VECTOR, "model");
  }

  @Test
  void aFailedEmbeddingIsCountedAndEndsTheRun() {
    when(repository.findWithoutEmbedding(anyInt(), anyInt())).thenReturn(List.of(TESTER, SALE));
    when(embeddings.embed("Tester", TitleMatchWorker.TASK_TYPE)).thenThrow(new IllegalStateException("down"));

    worker.run();

    verify(repository).countFailure("tester");
    verify(embeddings, never()).embed("Sale", TitleMatchWorker.TASK_TYPE);
    verify(repository, never()).findRequested(any(), anyInt());
  }

  @Test
  void everyJudgedTitleIsSavedWhetherItMatchesOrNot() {
    givenRequested(TESTER);
    givenUnjudged("tester", new Title("qa", "QA Engineer"), new Title("dev", "Developer"), new Title("qc", "QC"));
    when(chat.generateJson(any(), contains("USER: Tester"))).thenReturn("{\"matches\": [1, 3, 9]}");

    worker.run();

    verify(repository).saveJudged("tester", List.of("qa", "qc"), List.of("dev"));
    verify(repository).markMatched("tester");
  }

  @Test
  void nothingNewToJudgeAsksNobody() {
    givenRequested(TESTER);
    givenUnjudged("tester");

    worker.run();

    verifyNoInteractions(chat);
    verify(repository).markMatched("tester");
  }

  @Test
  void anUnusableAnswerSavesNothingAndLetsTheOthersGoFirstNextTime() {
    givenRequested(TESTER, SALE);
    givenUnjudged("tester", new Title("qa", "QA Engineer"));
    when(chat.generateJson(any(), any())).thenReturn("{\"matches\": \"none\"}");

    worker.run();

    verify(repository, never()).saveJudged(any(), any(), any());
    verify(repository).markMatched("tester");
    verify(repository, never()).markMatched("sale");
  }

  private void givenRequested(Title... titles) {
    when(repository.findRequested(any(), anyInt())).thenReturn(List.of(titles));
  }

  private void givenUnjudged(String userTitle, Title... jobs) {
    when(repository.findUnjudged(eq(userTitle), anyInt(), anyDouble(), anyInt())).thenReturn(List.of(jobs));
  }
}
