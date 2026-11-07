package com.jobbeacon.graph;

import com.jobbeacon.entity.GraphSyncOutbox;
import com.jobbeacon.repository.GraphSyncOutboxRepository;
import com.jobbeacon.utils.UuidUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

/**
 * Records that an entity changed and must be re-synced to the graph. Call it inside the transaction that makes the
 * change, so the row commits (or rolls back) together with it.
 */
@Service
public class GraphSyncOutboxService {

    private final GraphSyncOutboxRepository outboxRepository;
    private final boolean enabled;

    public GraphSyncOutboxService(
        GraphSyncOutboxRepository outboxRepository,
        @Value("${app.graph.enabled:false}") boolean enabled
    ) {
        this.outboxRepository = outboxRepository;
        this.enabled = enabled;
    }

    public void jobChanged(UUID jobId) {
        append(GraphEntityType.JOB, jobId);
    }

    private void append(GraphEntityType type, UUID entityId) {
        // Nothing would drain the table; enabling the graph later starts from a backfill instead.
        if (!enabled) {
            return;
        }
        GraphSyncOutbox row = new GraphSyncOutbox();
        row.setId(UuidUtils.v7());
        row.setEntityType(type);
        row.setEntityId(entityId);
        row.setCreatedAt(Instant.now());
        outboxRepository.save(row);
    }
}
