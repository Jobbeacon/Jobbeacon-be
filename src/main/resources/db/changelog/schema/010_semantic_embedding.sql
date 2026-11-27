-- Matching titles by meaning through the LLM (009) is replaced by comparing CVs with jobs by meaning, below.
DROP TABLE IF EXISTS title_match;
DROP TABLE IF EXISTS title_embedding;
DROP INDEX IF EXISTS idx_job_title_key;
ALTER TABLE job DROP COLUMN IF EXISTS title_key;
DROP FUNCTION IF EXISTS title_key(TEXT);

-- What a job is about, as a vector to compare with candidates (SemanticEmbeddingWorker). Rows are added or marked stale
-- when the job changes and filled in the background, so no request and no crawled posting waits for Gemini; a stale
-- row keeps serving its old vector until the new one is in.
CREATE TABLE IF NOT EXISTS job_embedding (
    job_id          UUID         NOT NULL,
    embedding       vector(1536),
    model           VARCHAR(100),
    -- Of the text the vector was made from: a job crawled again with the same text is not embedded again.
    text_hash       VARCHAR(64),
    stale           BOOLEAN      NOT NULL DEFAULT TRUE,
    attempts        INT          NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    embedded_at     TIMESTAMPTZ,

    CONSTRAINT pk_job_embedding PRIMARY KEY (job_id),
    CONSTRAINT fk_job_embedding_job FOREIGN KEY (job_id) REFERENCES job (id) ON DELETE CASCADE
        -- Checked at commit: a crawled job is marked before JPA has written it.
        DEFERRABLE INITIALLY DEFERRED
);

CREATE INDEX IF NOT EXISTS idx_job_embedding_pending ON job_embedding (next_attempt_at) WHERE stale;

-- The same for what a candidate looks for: their desired titles and their CV.
CREATE TABLE IF NOT EXISTS candidate_embedding (
    user_id         UUID         NOT NULL,
    embedding       vector(1536),
    model           VARCHAR(100),
    text_hash       VARCHAR(64),
    stale           BOOLEAN      NOT NULL DEFAULT TRUE,
    attempts        INT          NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    embedded_at     TIMESTAMPTZ,

    CONSTRAINT pk_candidate_embedding PRIMARY KEY (user_id),
    CONSTRAINT fk_candidate_embedding_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE DEFERRABLE INITIALLY DEFERRED
);

CREATE INDEX IF NOT EXISTS idx_candidate_embedding_pending ON candidate_embedding (next_attempt_at) WHERE stale;
