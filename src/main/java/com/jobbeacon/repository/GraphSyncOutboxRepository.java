package com.jobbeacon.repository;

import com.jobbeacon.entity.GraphSyncOutbox;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface GraphSyncOutboxRepository extends BaseRepository<GraphSyncOutbox> {

    @Query("""
            select o
            from GraphSyncOutbox o
            where o.processedAt is null and o.attempts < :maxAttempts
            order by o.createdAt asc
            """)
    List<GraphSyncOutbox> findPending(@Param("maxAttempts") int maxAttempts, Pageable pageable);

    @Transactional
    @Modifying
    @Query("update GraphSyncOutbox o set o.processedAt = :processedAt where o.id in :ids")
    int markProcessed(@Param("ids") Collection<UUID> ids, @Param("processedAt") Instant processedAt);

    @Transactional
    @Modifying
    @Query("update GraphSyncOutbox o set o.attempts = o.attempts + 1, o.lastError = :error where o.id in :ids")
    int recordFailure(@Param("ids") Collection<UUID> ids, @Param("error") String error);

    @Transactional
    @Modifying
    @Query(value = """
            INSERT INTO graph_sync_outbox (id, entity_type, entity_id, created_at, attempts)
            SELECT gen_random_uuid(), 'JOB', id, now(), 0 FROM job
            """, nativeQuery = true)
    int enqueueAllJobs();
}
