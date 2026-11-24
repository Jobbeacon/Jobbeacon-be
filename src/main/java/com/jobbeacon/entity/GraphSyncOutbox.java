package com.jobbeacon.entity;

import com.jobbeacon.graph.GraphEntityType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "graph_sync_outbox")
public class GraphSyncOutbox {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "entity_type", nullable = false, length = 30)
    private GraphEntityType entityType;

    @Column(name = "entity_id", nullable = false)
    private UUID entityId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
