CREATE TABLE IF NOT EXISTS datasets (
       id BIGSERIAL PRIMARY KEY,
       name VARCHAR(255) NOT NULL,
       description VARCHAR(255),
       owner_id BIGINT NOT NULL REFERENCES users(id),
       collection_id BIGINT,
       import_mappings TEXT,
       facility_count BIGINT
);

