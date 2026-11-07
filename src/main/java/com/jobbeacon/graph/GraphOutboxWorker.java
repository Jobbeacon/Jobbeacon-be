package com.jobbeacon.graph;

import com.jobbeacon.entity.GraphSyncOutbox;
import com.jobbeacon.repository.GraphSyncOutboxRepository;
import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.exceptions.ServiceUnavailableException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Drains {@code graph_sync_outbox} into Neo4j. While Neo4j is unreachable rows simply stay pending; a row that fails
 * for any other reason has its attempt count bumped and is retried until {@code app.graph.max-attempts}.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.graph.enabled", havingValue = "true")
public class GraphOutboxWorker {

    private static final int MAX_ERROR_LENGTH = 2000;

    private final GraphSyncOutboxRepository outboxRepository;
    private final GraphSyncService graphSyncService;
    private final int batchSize;
    private final int maxAttempts;

    public GraphOutboxWorker(
        GraphSyncOutboxRepository outboxRepository,
        GraphSyncService graphSyncService,
        GraphProperties properties
    ) {
        this.outboxRepository = outboxRepository;
        this.graphSyncService = graphSyncService;
        this.batchSize = properties.batchSize();
        this.maxAttempts = properties.maxAttempts();
    }

    // Fixed delay (not rate) so a slow batch never overlaps the next poll.
    @Scheduled(initialDelayString = "${app.graph.poll-interval}", fixedDelayString = "${app.graph.poll-interval}")
    public void poll() {
        List<GraphSyncOutbox> rows;
        do {
            rows = outboxRepository.findPending(maxAttempts, PageRequest.of(0, batchSize));
            if (rows.isEmpty() || !process(rows)) {
                return;
            }
        } while (rows.size() == batchSize);
    }

    /** Returns false when the rest of the backlog should wait for the next poll. */
    private boolean process(List<GraphSyncOutbox> rows) {
        // Several rows for the same job collapse into one sync, since the sync reads current state anyway.
        Map<UUID, List<UUID>> rowIdsByJob = new LinkedHashMap<>();
        List<UUID> unknownRowIds = new ArrayList<>();
        for (GraphSyncOutbox row : rows) {
            if (row.getEntityType() == GraphEntityType.JOB) {
                rowIdsByJob.computeIfAbsent(row.getEntityId(), id -> new ArrayList<>()).add(row.getId());
            } else {
                unknownRowIds.add(row.getId());
            }
        }
        if (!unknownRowIds.isEmpty()) {
            outboxRepository.recordFailure(unknownRowIds, "Unsupported entity type");
        }

        try {
            graphSyncService.syncJobs(rowIdsByJob.keySet());
            outboxRepository.markProcessed(allRowIds(rowIdsByJob.values()), Instant.now());
            log.info("Synced {} jobs to graph from {} outbox rows", rowIdsByJob.size(), rows.size());
            return true;
        } catch (ServiceUnavailableException e) {
            // Neo4j is down: not the rows' fault, so attempts are left untouched and the rows wait however long the
            // outage lasts. Retrying job by job would only fail slower.
            log.warn("Neo4j unavailable, {} outbox rows stay pending: {}", rows.size(), e.getMessage());
            return false;
        } catch (Exception e) {
            log.warn("Graph sync batch failed, retrying job by job: {}", e.getMessage(), e);
            rowIdsByJob.forEach(this::processSingle);
            return true;
        }
    }

    // Isolates a bad job so it cannot keep the rest of its batch from syncing.
    private void processSingle(UUID jobId, List<UUID> rowIds) {
        try {
            graphSyncService.syncJobs(Set.of(jobId));
            outboxRepository.markProcessed(rowIds, Instant.now());
        } catch (Exception e) {
            log.warn("Graph sync failed for job {}: {}", jobId, e.getMessage());
            outboxRepository.recordFailure(rowIds, truncate(e.getMessage()));
        }
    }

    private static List<UUID> allRowIds(Collection<List<UUID>> groups) {
        return groups.stream().flatMap(List::stream).toList();
    }

    private static String truncate(String message) {
        String text = String.valueOf(message);
        return text.length() <= MAX_ERROR_LENGTH ? text : text.substring(0, MAX_ERROR_LENGTH);
    }
}
