package com.jobbeacon.internal;

import com.jobbeacon.repository.SemanticEmbeddingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/** Open jobs closest in meaning to what a user looks for, from the vectors {@code SemanticEmbeddingWorker} keeps. */
@Service
@RequiredArgsConstructor
public class SemanticJobService {

    private final SemanticEmbeddingRepository repository;

    @Value("${app.semantic-matching.enabled}")
    private boolean enabled;

    public List<UUID> findJobIds(UUID userId, int limit) {
        return enabled && limit > 0 ? repository.findNearestOpenJobs(userId, limit) : List.of();
    }
}
