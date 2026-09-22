-- ---------- TABLE USER -----------
CREATE TABLE IF NOT EXISTS users (
  id               UUID,
  firebase_uid     VARCHAR(128) NOT NULL,
  email            VARCHAR(255) NOT NULL,
  email_verified   BOOLEAN NOT NULL DEFAULT FALSE,
  display_name     VARCHAR(150),
  avatar_url       TEXT,
  user_role             VARCHAR(50) NOT NULL DEFAULT 'USER',
  status           VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
  created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
  last_login_at    TIMESTAMPTZ,

  CONSTRAINT pk_user PRIMARY KEY (id)
);

-- ---------- TABLE CV_PROFILE -----------
CREATE TABLE IF NOT EXISTS cv_profile (
  id                        UUID,
  user_id                   UUID,
  file_url                  TEXT,
  parse_status              VARCHAR(50),
  total_months_experience   INT,
  experiences               JSONB,
  educations                JSONB,
  parser_model_version      VARCHAR(50),
  uploaded_at               TIMESTAMPTZ,
  parsed_at                 TIMESTAMPTZ,
  updated_at                TIMESTAMPTZ,

  CONSTRAINT pk_cv_profile PRIMARY KEY (id)
);

-- ---------- TABLE CV_SKILL_MAPPING -----------
CREATE TABLE IF NOT EXISTS cv_skill_mapping (
  cv_profile_id   UUID,
  skill_id        UUID,

  CONSTRAINT pk_cv_skill_mapping PRIMARY KEY (cv_profile_id, skill_id)
);

-- ---------- TABLE JOB -----------
CREATE TABLE IF NOT EXISTS job (
  id                  UUID,
  company_id          UUID,
  title               VARCHAR(255),
  description         TEXT,
  experience_months   INT,
  employment_type     VARCHAR(100),
  posted_date         TIMESTAMPTZ,
  expired_date        TIMESTAMPTZ,
  min_salary          BIGINT,
  max_salary          BIGINT,
  currency            VARCHAR(100),
  salary_period       VARCHAR(100),
  location            TEXT[],
  benefits            TEXT,

  CONSTRAINT pk_job PRIMARY KEY (id)
);

-- ---------- TABLE COMPANY -----------
CREATE TABLE IF NOT EXISTS company (
  id         UUID,
  name       VARCHAR(255) NOT NULL,
  logo_url   TEXT,

  CONSTRAINT pk_company PRIMARY KEY (id)
);

-- ---------- TABLE CATEGORY -----------
CREATE TABLE IF NOT EXISTS category (
  id          UUID,
  name        VARCHAR(255),
  parent_id   UUID,

  CONSTRAINT pk_category PRIMARY KEY (id)
);

-- ---------- TABLE JOB_CATEGORY_MAPPING -----------
CREATE TABLE IF NOT EXISTS job_category_mapping (
  job_id        UUID,
  category_id   UUID,

  CONSTRAINT pk_job_category_mapping PRIMARY KEY (job_id, category_id)
);

-- ---------- TABLE SKILL -----------
CREATE TABLE IF NOT EXISTS skill (
  id     UUID,
  name   VARCHAR(255),

  CONSTRAINT pk_skill PRIMARY KEY (id)
);

-- ---------- TABLE JOB_SKILL_MAPPING -----------
CREATE TABLE IF NOT EXISTS job_skill_mapping (
  job_id     UUID,
  skill_id   UUID,

  CONSTRAINT pk_job_skill_mapping PRIMARY KEY (job_id, skill_id)
);

-- ---------- TABLE JOB_SOURCE -----------
CREATE TABLE IF NOT EXISTS job_source (
  id       UUID,
  domain   VARCHAR(255),

  CONSTRAINT pk_job_source PRIMARY KEY (id)
);

-- ---------- TABLE JOB_SOURCE_MAPPING -----------
CREATE TABLE IF NOT EXISTS job_source_mapping (
  source_id    UUID,
  job_id       UUID,
  job_url      VARCHAR(500),
  crawled_at   TIMESTAMPTZ,

  CONSTRAINT pk_job_source_mapping PRIMARY KEY (source_id, job_id)
);

-- ---------- FOREIGN KEYS -----------
ALTER TABLE cv_profile           ADD CONSTRAINT fk_cv_profile_user_id FOREIGN KEY (user_id) REFERENCES users(id);
ALTER TABLE cv_skill_mapping     ADD CONSTRAINT fk_cv_skill_mapping_cv_profile_id FOREIGN KEY (cv_profile_id) REFERENCES cv_profile(id);
ALTER TABLE cv_skill_mapping     ADD CONSTRAINT fk_cv_skill_mapping_skill_id FOREIGN KEY (skill_id) REFERENCES skill(id);
ALTER TABLE job                  ADD CONSTRAINT fk_job_company_id FOREIGN KEY (company_id) REFERENCES company(id);
ALTER TABLE category             ADD CONSTRAINT fk_category_parent_id FOREIGN KEY (parent_id) REFERENCES category(id);
ALTER TABLE job_category_mapping ADD CONSTRAINT fk_job_category_mapping_job_id FOREIGN KEY (job_id) REFERENCES job(id);
ALTER TABLE job_category_mapping ADD CONSTRAINT fk_job_category_mapping_category_id FOREIGN KEY (category_id) REFERENCES category(id);
ALTER TABLE job_skill_mapping    ADD CONSTRAINT fk_job_skill_mapping_job_id FOREIGN KEY (job_id) REFERENCES job(id);
ALTER TABLE job_skill_mapping    ADD CONSTRAINT fk_job_skill_mapping_skill_id FOREIGN KEY (skill_id) REFERENCES skill(id);
ALTER TABLE job_source_mapping   ADD CONSTRAINT fk_job_source_mapping_source_id FOREIGN KEY (source_id) REFERENCES job_source(id);
ALTER TABLE job_source_mapping   ADD CONSTRAINT fk_job_source_mapping_job_id FOREIGN KEY (job_id) REFERENCES job(id);
