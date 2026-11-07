package com.jobbeacon.graph;

import com.jobbeacon.repository.GraphSyncOutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

/**
 * Seeds the graph with jobs that existed before the outbox did (or before the graph was enabled) by queueing every
 * job; {@link GraphOutboxWorker} then syncs them in batches. Re-running is harmless, only redundant work.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnExpression("${app.graph.enabled:false} and ${app.graph.backfill-on-startup:false}")
public class GraphBackfillRunner implements ApplicationRunner {

    private final GraphSyncOutboxRepository outboxRepository;

    @Override
    public void run(ApplicationArguments args) {
        int queued = outboxRepository.enqueueAllJobs();
        log.info("Queued {} jobs for graph backfill", queued);
    }
}
