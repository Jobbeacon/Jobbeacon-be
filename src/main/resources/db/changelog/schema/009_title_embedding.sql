-- What makes two titles the same text. Immutable so that the generated column below can use it.
CREATE OR REPLACE FUNCTION title_key(title TEXT) RETURNS TEXT
    LANGUAGE sql IMMUTABLE PARALLEL SAFE
    AS 'SELECT nullif(left(lower(btrim(regexp_replace(title, ''\s+'', '' '', ''g''))), 255), '''')';

-- Job titles and the titles users look for, one row per distinct title. Rows are added without an embedding and filled in
-- the background (TitleMatchWorker), so no request waits for Gemini.
CREATE TABLE IF NOT EXISTS title_embedding (
    title_key    VARCHAR(255) NOT NULL,
    title        VARCHAR(255) NOT NULL,
    embedding    vector(1536),
    model        VARCHAR(100),
    attempts     INT          NOT NULL DEFAULT 0,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    -- When a user last looked for this title; null for a title only jobs have.
    requested_at TIMESTAMPTZ,
    -- When it was last compared with job titles.
    matched_at   TIMESTAMPTZ,

    CONSTRAINT pk_title_embedding PRIMARY KEY (title_key)
);

CREATE INDEX IF NOT EXISTS idx_title_embedding_pending ON title_embedding (created_at) WHERE embedding IS NULL;

-- Whether a job title names the occupation a user looks for, as judged by the LLM. Embeddings only pick the pairs to
-- judge: they put "Business Analyst" closer to "Data Analyst" than "Accountant" to "Kế toán".
CREATE TABLE IF NOT EXISTS title_match (
    user_title_key VARCHAR(255) NOT NULL,
    job_title_key  VARCHAR(255) NOT NULL,
    matches        BOOLEAN      NOT NULL,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT pk_title_match PRIMARY KEY (user_title_key, job_title_key),
    CONSTRAINT fk_title_match_user_title FOREIGN KEY (user_title_key) REFERENCES title_embedding (title_key) ON DELETE CASCADE,
    CONSTRAINT fk_title_match_job_title FOREIGN KEY (job_title_key) REFERENCES title_embedding (title_key) ON DELETE CASCADE
);

ALTER TABLE job ADD COLUMN IF NOT EXISTS title_key VARCHAR(255) GENERATED ALWAYS AS (title_key(title)) STORED;

CREATE INDEX IF NOT EXISTS idx_job_title_key ON job (title_key);
