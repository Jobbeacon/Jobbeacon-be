CREATE TABLE job_location (
    id            UUID PRIMARY KEY,
    job_id        UUID NOT NULL,
    raw_location  TEXT NOT NULL,
    province_id   VARCHAR(20),
    ward_id       VARCHAR(20),

    CONSTRAINT fk_job_location_job
        FOREIGN KEY (job_id) REFERENCES job(id),
    CONSTRAINT fk_job_location_province
        FOREIGN KEY (province_id) REFERENCES province(id),
    CONSTRAINT fk_job_location_ward
        FOREIGN KEY (ward_id) REFERENCES ward(id)
);
