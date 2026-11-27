package com.jobbeacon.semantic;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jobbeacon.client.GeminiEmbeddingClient;
import com.jobbeacon.entity.CvProfile;
import com.jobbeacon.repository.CvProfileRepository;
import com.jobbeacon.repository.SemanticEmbeddingRepository;
import com.jobbeacon.repository.SemanticEmbeddingRepository.Kind;
import com.jobbeacon.repository.SemanticEmbeddingRepository.Pending;
import com.jobbeacon.repository.SemanticEmbeddingRepository.PendingJob;
import com.jobbeacon.repository.SemanticEmbeddingRepository.State;
import com.jobbeacon.repository.UserJobPreferenceRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class SemanticEmbeddingWorkerTest {

  private static final String MODEL = "model-a";
  private static final State NEW = new State(null, null, OffsetDateTime.now());
  private static final float[] VECTOR = {1f};

  private final SemanticEmbeddingRepository repository = mock(SemanticEmbeddingRepository.class);
  private final CvProfileRepository cvProfiles = mock(CvProfileRepository.class);
  private final UserJobPreferenceRepository preferences = mock(UserJobPreferenceRepository.class);
  private final GeminiEmbeddingClient embeddings = mock(GeminiEmbeddingClient.class);
  private final SemanticEmbeddingWorker worker =
      new SemanticEmbeddingWorker(repository, cvProfiles, preferences, embeddings);

  @BeforeEach
  void setUp() {
    ReflectionTestUtils.setField(worker, "batchSize", 50);
    ReflectionTestUtils.setField(worker, "model", MODEL);
    when(preferences.findByUserId(any())).thenReturn(Optional.empty());
    when(cvProfiles.findByUserId(any())).thenReturn(Optional.empty());
    when(repository.findCandidateSkills(any())).thenReturn(List.of());
  }

  @Test
  void candidatesAndJobsAreEmbeddedAsTheTwoSidesOfASearch() {
    UUID user = candidateWithCv("Tester");
    PendingJob job = job("Automation Tester", NEW);
    when(embeddings.embed(anyString(), anyString())).thenReturn(VECTOR);

    worker.run();

    verify(repository).addMissing(MODEL);
    verify(embeddings).embed("Current position: Tester", SemanticEmbeddingWorker.CANDIDATE_TASK_TYPE);
    verify(embeddings).embed("Job title: Automation Tester", SemanticEmbeddingWorker.JOB_TASK_TYPE);
    verify(repository).save(Kind.CANDIDATE, user, NEW, VECTOR, MODEL, SemanticTexts.hash("Current position: Tester"));
    verify(repository).save(Kind.JOB, job.id(), NEW, VECTOR, MODEL, SemanticTexts.hash("Job title: Automation Tester"));
  }

  @Test
  void unchangedTextIsNotEmbeddedAgain() {
    State seen = new State(SemanticTexts.hash("Job title: Tester"), MODEL, OffsetDateTime.now());
    PendingJob job = job("Tester", seen);
    when(repository.findPendingCandidates(50)).thenReturn(List.of());

    worker.run();

    verify(repository).keep(Kind.JOB, job.id(), seen);
    verify(embeddings, never()).embed(anyString(), anyString());
  }

  @Test
  void theSameTextOfAnotherModelIsEmbeddedAgain() {
    PendingJob job = job("Tester", new State(SemanticTexts.hash("Job title: Tester"), "model-b", OffsetDateTime.now()));
    when(repository.findPendingCandidates(50)).thenReturn(List.of());
    when(embeddings.embed(anyString(), anyString())).thenReturn(VECTOR);

    worker.run();

    verify(repository).save(eq(Kind.JOB), eq(job.id()), any(), eq(VECTOR), eq(MODEL), anyString());
  }

  @Test
  void aCandidateWithNothingToGoByLosesTheVector() {
    UUID user = UUID.randomUUID();
    when(repository.findPendingCandidates(50)).thenReturn(List.of(new Pending(user, NEW)));

    worker.run();

    verify(repository).clear(Kind.CANDIDATE, user, NEW, MODEL);
    verify(embeddings, never()).embed(anyString(), anyString());
  }

  @Test
  void aFailureIsCountedAndEndsTheRun() {
    UUID user = candidateWithCv("Tester");
    job("Tester", NEW);
    when(embeddings.embed(anyString(), anyString())).thenThrow(new IllegalStateException("down"));

    worker.run();

    verify(repository).countFailure(Kind.CANDIDATE, user);
    verify(repository, never()).findPendingJobs(50);
  }

  private UUID candidateWithCv(String title) {
    UUID user = UUID.randomUUID();
    CvProfile cv = new CvProfile();
    cv.setCurrentTitle(title);
    when(cvProfiles.findByUserId(user)).thenReturn(Optional.of(cv));
    when(repository.findPendingCandidates(50)).thenReturn(List.of(new Pending(user, NEW)));
    return user;
  }

  private PendingJob job(String title, State state) {
    PendingJob job = new PendingJob(UUID.randomUUID(), title, null, List.of(), List.of(), state);
    when(repository.findPendingJobs(50)).thenReturn(List.of(job));
    return job;
  }
}
