CREATE TABLE job_search_outbox (
    id          UUID PRIMARY KEY,
    job_id      UUID NOT NULL,
    op          VARCHAR(1) NOT NULL,
    payload     JSONB,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_job_search_outbox_op CHECK (op IN ('c', 'u', 'd'))
);

CREATE INDEX idx_job_search_outbox_job_created
    ON job_search_outbox (job_id, created_at, id);

CREATE TABLE debezium_heartbeat (
    id  SMALLINT PRIMARY KEY,
    ts  TIMESTAMPTZ NOT NULL
);

INSERT INTO debezium_heartbeat (id, ts) VALUES (1, now());
