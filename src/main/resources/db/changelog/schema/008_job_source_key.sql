-- Identifies a posting within its source, so that receiving it again updates the job instead of adding a copy.
-- Must match com.jobbeacon.utils.SourceJobKeys.
ALTER TABLE job_source_mapping ADD COLUMN IF NOT EXISTS source_job_key VARCHAR(500);

-- Copies stored before this column existed are kept: only the first one of each posting gets the key (later messages
-- update that one) and the others stay NULL, which the unique index ignores.
WITH paths AS (
    SELECT m.source_id, m.job_id, m.crawled_at, s.domain,
           rtrim(regexp_replace(regexp_replace(m.job_url, '[?#].*$', ''), '^https?://[^/]*', '', 'i'), '/') AS path
    FROM job_source_mapping m
    JOIN job_source s ON s.id = m.source_id
),
keys AS (
    SELECT source_id, job_id, crawled_at,
           CASE WHEN lower(domain) = 'vietnamworks.com'
                THEN coalesce(substring(path from '-([0-9]+)-j[vd]$'), nullif(path, ''))
                ELSE nullif(path, '')
           END AS source_job_key
    FROM paths
),
ranked AS (
    SELECT source_id, job_id, source_job_key,
           row_number() OVER (PARTITION BY source_id, source_job_key ORDER BY crawled_at NULLS LAST, job_id) AS copy
    FROM keys
    WHERE source_job_key IS NOT NULL
)
UPDATE job_source_mapping m
SET source_job_key = r.source_job_key
FROM ranked r
WHERE r.source_id = m.source_id AND r.job_id = m.job_id AND r.copy = 1 AND m.source_job_key IS NULL
  AND NOT EXISTS (
      SELECT 1 FROM job_source_mapping k WHERE k.source_id = r.source_id AND k.source_job_key = r.source_job_key
  );

CREATE UNIQUE INDEX IF NOT EXISTS uk_job_source_mapping_source_job_key
    ON job_source_mapping (source_id, source_job_key);
