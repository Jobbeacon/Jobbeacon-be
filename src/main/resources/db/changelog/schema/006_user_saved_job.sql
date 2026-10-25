CREATE TABLE IF NOT EXISTS user_saved_job (
    user_id    UUID        NOT NULL,
    job_id     UUID        NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT pk_user_saved_job PRIMARY KEY (user_id, job_id),
    CONSTRAINT fk_user_saved_job_user_id
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_user_saved_job_job_id
        FOREIGN KEY (job_id) REFERENCES job(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_user_saved_job_user_created
    ON user_saved_job (user_id, created_at DESC);
