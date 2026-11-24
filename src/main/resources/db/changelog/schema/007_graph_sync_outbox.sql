-- Changes waiting to be pushed to the recommendation graph. Rows are written in the same transaction as the change
-- itself, so a committed change is never lost. The recommendation service streams this table through CDC and re-reads
-- the entity's current state, which makes a duplicate row harmless.
CREATE TABLE IF NOT EXISTS graph_sync_outbox (
    id            UUID        NOT NULL,
    entity_type   VARCHAR(30) NOT NULL,
    entity_id     UUID        NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT pk_graph_sync_outbox PRIMARY KEY (id)
);
