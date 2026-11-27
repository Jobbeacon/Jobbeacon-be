package com.jobbeacon.semantic;

import com.jobbeacon.client.GeminiEmbeddingClient;
import com.jobbeacon.entity.CvProfile;
import com.jobbeacon.entity.UserJobPreference;
import com.jobbeacon.repository.CvProfileRepository;
import com.jobbeacon.repository.SemanticEmbeddingRepository;
import com.jobbeacon.repository.SemanticEmbeddingRepository.Kind;
import com.jobbeacon.repository.SemanticEmbeddingRepository.Pending;
import com.jobbeacon.repository.SemanticEmbeddingRepository.PendingJob;
import com.jobbeacon.repository.SemanticEmbeddingRepository.State;
import com.jobbeacon.repository.UserJobPreferenceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Embeds what jobs are about and what candidates look for, so that the recommendation service can compare them by
 * meaning. Works through the rows marked stale (changed, new, or from before the tables existed); a run stops at the
 * first failure, since Gemini failing for one text usually fails for the next, and the failed row waits longer each time.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.semantic-matching.enabled", havingValue = "true")
public class SemanticEmbeddingWorker {

    // A candidate is what is searched with, a job what is searched for: Gemini embeds the two sides accordingly.
    static final String CANDIDATE_TASK_TYPE = "RETRIEVAL_QUERY";
    static final String JOB_TASK_TYPE = "RETRIEVAL_DOCUMENT";

    private final SemanticEmbeddingRepository repository;
    private final CvProfileRepository cvProfileRepository;
    private final UserJobPreferenceRepository userJobPreferenceRepository;
    private final GeminiEmbeddingClient embeddingClient;

    @Value("${app.semantic-matching.batch-size}")
    private int batchSize;

    @Value("${app.gemini.embedding-model}")
    private String model;

    @Scheduled(fixedDelayString = "${app.semantic-matching.interval}", initialDelayString = "${app.semantic-matching.interval}")
    public void run() {
        repository.addMissing(model);
        // Candidates first: there are few, and without theirs no job vector is of use to them.
        if (embedCandidates()) {
            embedJobs();
        }
    }

    private boolean embedCandidates() {
        for (Pending candidate : repository.findPendingCandidates(batchSize)) {
            UUID userId = candidate.id();
            UserJobPreference preference = userJobPreferenceRepository.findByUserId(userId).orElse(null);
            CvProfile cv = cvProfileRepository.findByUserId(userId).orElse(null);
            String text = SemanticTexts.candidate(preference, cv, repository.findCandidateSkills(userId));
            if (!embed(Kind.CANDIDATE, userId, candidate.state(), text, CANDIDATE_TASK_TYPE)) {
                return false;
            }
        }
        return true;
    }

    private void embedJobs() {
        for (PendingJob job : repository.findPendingJobs(batchSize)) {
            String text = SemanticTexts.job(job.title(), job.categories(), job.skills(), job.description());
            if (!embed(Kind.JOB, job.id(), job.state(), text, JOB_TASK_TYPE)) {
                return;
            }
        }
    }

    /** False when Gemini failed. */
    private boolean embed(Kind kind, UUID id, State seen, String text, String taskType) {
        if (text.isBlank()) {
            repository.clear(kind, id, seen, model);
            return true;
        }
        String hash = SemanticTexts.hash(text);
        if (hash.equals(seen.textHash()) && model.equals(seen.model())) {
            repository.keep(kind, id, seen);
            return true;
        }
        float[] embedding;
        try {
            embedding = embeddingClient.embed(text, taskType);
        } catch (RuntimeException e) {
            repository.countFailure(kind, id);
            log.warn("Embedding {} {} failed, trying again later: {}", kind, id, e.getMessage());
            return false;
        }
        repository.save(kind, id, seen, embedding, model, hash);
        return true;
    }

    @Configuration
    @EnableScheduling
    @ConditionalOnProperty(name = "app.semantic-matching.enabled", havingValue = "true")
    static class Scheduling {
    }
}
