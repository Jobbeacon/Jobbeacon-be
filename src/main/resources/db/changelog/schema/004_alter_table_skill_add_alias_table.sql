ALTER TABLE skill
    ADD COLUMN IF NOT EXISTS normalized_name VARCHAR(255),
    ADD COLUMN IF NOT EXISTS embedding vector(1536);

CREATE UNIQUE INDEX IF NOT EXISTS uq_skill_normalized_name
    ON skill (normalized_name);

CREATE TABLE IF NOT EXISTS skill_alias (
    id               UUID,
    alias            VARCHAR(255) NOT NULL,
    normalized_alias VARCHAR(255) NOT NULL,
    skill_id         UUID NOT NULL,

    CONSTRAINT pk_skill_alias PRIMARY KEY (id),

    CONSTRAINT fk_skill_alias_skill
    FOREIGN KEY (skill_id)
    REFERENCES skill (id)
    ON DELETE CASCADE
    );

CREATE UNIQUE INDEX IF NOT EXISTS uq_skill_alias_normalized
    ON skill_alias (normalized_alias);

CREATE INDEX IF NOT EXISTS idx_skill_alias_skill_id
    ON skill_alias (skill_id);
