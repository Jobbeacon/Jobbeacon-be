-- ---------- CV_SKILL_MAPPING: FK cascade theo cv_profile -----------
ALTER TABLE cv_skill_mapping DROP CONSTRAINT IF EXISTS fk_cv_skill_mapping_cv_profile_id;
ALTER TABLE cv_skill_mapping ADD CONSTRAINT fk_cv_skill_mapping_cv_profile_id
    FOREIGN KEY (cv_profile_id) REFERENCES cv_profile(id) ON DELETE CASCADE;

-- ---------- CV_PROFILE: dọn dữ liệu trước NOT NULL + UNIQUE(user_id) -----------
DELETE FROM cv_skill_mapping
WHERE cv_profile_id IN (SELECT id FROM cv_profile WHERE user_id IS NULL);

DELETE FROM cv_profile WHERE user_id IS NULL;

DELETE FROM cv_skill_mapping
WHERE cv_profile_id IN (
    SELECT ranked.id
    FROM (
        SELECT id,
               row_number() OVER (
                   PARTITION BY user_id
                   ORDER BY COALESCE(updated_at, parsed_at, uploaded_at) DESC NULLS LAST, id DESC
               ) AS rn
        FROM cv_profile
    ) ranked
    WHERE ranked.rn > 1
);

DELETE FROM cv_profile
WHERE id IN (
    SELECT ranked.id
    FROM (
        SELECT id,
               row_number() OVER (
                   PARTITION BY user_id
                   ORDER BY COALESCE(updated_at, parsed_at, uploaded_at) DESC NULLS LAST, id DESC
               ) AS rn
        FROM cv_profile
    ) ranked
    WHERE ranked.rn > 1
);

-- ---------- CV_PROFILE: kết quả parse mới nhất của user (1 profile / user) -----------
ALTER TABLE cv_profile DROP COLUMN IF EXISTS file_url;
ALTER TABLE cv_profile DROP COLUMN IF EXISTS parse_status;
ALTER TABLE cv_profile ADD COLUMN current_title VARCHAR(255);
ALTER TABLE cv_profile ADD COLUMN summary       TEXT;
ALTER TABLE cv_profile ADD COLUMN created_at    TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE cv_profile ALTER COLUMN updated_at SET DEFAULT now();
ALTER TABLE cv_profile ALTER COLUMN user_id SET NOT NULL;
ALTER TABLE cv_profile ADD CONSTRAINT uq_cv_profile_user UNIQUE (user_id);

ALTER TABLE cv_profile DROP CONSTRAINT IF EXISTS fk_cv_profile_user_id;
ALTER TABLE cv_profile ADD CONSTRAINT fk_cv_profile_user_id
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;

-- ---------- CV_SKILL_MAPPING: field cho recommendation -----------
ALTER TABLE cv_skill_mapping ADD COLUMN confidence        NUMERIC(4,3);
ALTER TABLE cv_skill_mapping ADD COLUMN experience_months INT;
ALTER TABLE cv_skill_mapping ADD COLUMN last_used_at      DATE;
ALTER TABLE cv_skill_mapping ADD COLUMN evidence_text     TEXT;

ALTER TABLE cv_skill_mapping ADD CONSTRAINT ck_cv_skill_mapping_confidence
    CHECK (confidence IS NULL OR confidence BETWEEN 0 AND 1);
ALTER TABLE cv_skill_mapping ADD CONSTRAINT ck_cv_skill_mapping_experience_months
    CHECK (experience_months IS NULL OR experience_months >= 0);
