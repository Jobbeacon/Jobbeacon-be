CREATE TABLE province (
      id VARCHAR(20) PRIMARY KEY,
      name_local VARCHAR(255) NOT NULL
);

CREATE TABLE ward (
      id VARCHAR(20) PRIMARY KEY,
      name_local VARCHAR(255) NOT NULL,
      province_id VARCHAR(20) NOT NULL,

      CONSTRAINT fk_ward_province
          FOREIGN KEY (province_id)
              REFERENCES province(id)
              ON DELETE RESTRICT
);