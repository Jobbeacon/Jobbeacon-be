-- ---------- USER_JOB_PREFERENCE: sở thích tìm việc của user (1 dòng / user, D-010) -----------
CREATE TABLE IF NOT EXISTS user_job_preference (
    id                  UUID        NOT NULL,
    user_id             UUID        NOT NULL,
    desired_titles      TEXT[],
    employment_types    TEXT[],
    remote_ok           BOOLEAN     NOT NULL DEFAULT FALSE,
    expected_salary_min BIGINT,
    expected_salary_max BIGINT,
    currency            VARCHAR(10) NOT NULL DEFAULT 'VND',
    salary_period       VARCHAR(20) NOT NULL DEFAULT 'MONTH',
    salary_negotiable   BOOLEAN     NOT NULL DEFAULT FALSE,
    open_to_work        BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ,

    CONSTRAINT pk_user_job_preference PRIMARY KEY (id),
    CONSTRAINT uq_user_job_preference_user UNIQUE (user_id),
    CONSTRAINT fk_user_job_preference_user_id
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT ck_user_job_preference_salary_min
        CHECK (expected_salary_min IS NULL OR expected_salary_min >= 0),
    CONSTRAINT ck_user_job_preference_salary_range
        CHECK (expected_salary_min IS NULL OR expected_salary_max IS NULL
               OR expected_salary_min <= expected_salary_max)
);

-- ---------- USER_PREFERRED_LOCATION: tỉnh/thành mong muốn của preference -----------
CREATE TABLE IF NOT EXISTS user_preferred_location (
    preference_id UUID        NOT NULL,
    province_id   VARCHAR(20) NOT NULL,

    CONSTRAINT pk_user_preferred_location PRIMARY KEY (preference_id, province_id),
    CONSTRAINT fk_user_preferred_location_preference_id
        FOREIGN KEY (preference_id) REFERENCES user_job_preference(id) ON DELETE CASCADE,
    CONSTRAINT fk_user_preferred_location_province_id
        FOREIGN KEY (province_id) REFERENCES province(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_user_preferred_location_province_id
    ON user_preferred_location (province_id);
