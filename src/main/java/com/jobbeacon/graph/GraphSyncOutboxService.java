package com.jobbeacon.graph;

import com.jobbeacon.entity.GraphSyncOutbox;
import com.jobbeacon.repository.GraphSyncOutboxRepository;
import com.jobbeacon.utils.UuidUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

/**
 * Records that an entity changed and must be re-synced to the recommendation graph. Call it inside the transaction
 * that makes the change, so the row commits (or rolls back) together with it. The recommendation service streams the
 * table through CDC and re-reads the entity's current state through the internal API.
 *
 * <p>Always written, even while the recommendation service is not running: its replication slot picks up from where
 * it stopped, and a first start replays the existing rows.
 */
@Service
@RequiredArgsConstructor
public class GraphSyncOutboxService {

    private final GraphSyncOutboxRepository outboxRepository;

    public void jobChanged(UUID jobId) {
        append(GraphEntityType.JOB, jobId);
    }

    /** The user's CV skills or saved jobs changed; the whole candidate is re-read when synced. */
    public void candidateChanged(UUID userId) {
        append(GraphEntityType.CANDIDATE, userId);
    }

    private void append(GraphEntityType type, UUID entityId) {
        GraphSyncOutbox row = new GraphSyncOutbox();
        row.setId(UuidUtils.v7());
        row.setEntityType(type);
        row.setEntityId(entityId);
        row.setCreatedAt(Instant.now());
        outboxRepository.save(row);
    }
}
