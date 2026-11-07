-- Changes waiting to be pushed to the Neo4j recommendation graph. Rows are written in the same transaction as the
-- change itself, so a committed change is never lost; the graph worker re-reads the entity's current state, which
-- makes reprocessing a row (retry, duplicate) harmless.
CREATE TABLE IF NOT EXISTS graph_sync_outbox (
    id            UUID        NOT NULL,
    entity_type   VARCHAR(30) NOT NULL,
    entity_id     UUID        NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    processed_at  TIMESTAMPTZ,
    attempts      INT         NOT NULL DEFAULT 0,
    last_error    TEXT,

    CONSTRAINT pk_graph_sync_outbox PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_graph_sync_outbox_pending
    ON graph_sync_outbox (created_at)
    WHERE processed_at IS NULL;
