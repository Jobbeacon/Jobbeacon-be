package com.jobbeacon.repository;

import com.jobbeacon.entity.GraphSyncOutbox;

/** Append-only: rows are read by the recommendation service through CDC, never queried here. */
public interface GraphSyncOutboxRepository extends BaseRepository<GraphSyncOutbox> {
}
