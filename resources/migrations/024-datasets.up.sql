CREATE TABLE IF NOT EXISTS datasets (
       id BIGSERIAL PRIMARY KEY,
       name VARCHAR(255) NOT NULL,
       owner_id BIGINT NOT NULL REFERENCES users(id),
       collection_id BIGINT,
       import_mappings TEXT
);

ALTER TABLE facilities ADD COLUMN dataset_id BIGINT REFERENCES datasets(id);
