-- Cross-site job deduplication: normalized company name, normalized title, exact content hash and MinHash signature
-- on job, plus LSH band buckets used to find near-duplicate candidates.
ALTER TABLE company ADD COLUMN IF NOT EXISTS normalized_name VARCHAR(255);
CREATE INDEX IF NOT EXISTS idx_company_normalized_name ON company (normalized_name);

ALTER TABLE job ADD COLUMN IF NOT EXISTS title_normalized TEXT;
ALTER TABLE job ADD COLUMN IF NOT EXISTS content_hash VARCHAR(64);
ALTER TABLE job ADD COLUMN IF NOT EXISTS minhash INTEGER[];
CREATE INDEX IF NOT EXISTS idx_job_company_content_hash ON job (company_id, content_hash);
CREATE INDEX IF NOT EXISTS idx_job_company_posted_date ON job (company_id, posted_date);

CREATE TABLE IF NOT EXISTS job_lsh_band (
    job_id    UUID     NOT NULL,
    band_idx  SMALLINT NOT NULL,
    band_hash BIGINT   NOT NULL,

    CONSTRAINT pk_job_lsh_band PRIMARY KEY (job_id, band_idx),
    CONSTRAINT fk_job_lsh_band_job FOREIGN KEY (job_id) REFERENCES job (id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_job_lsh_band_bucket ON job_lsh_band (band_idx, band_hash);
