ALTER TABLE projects ADD COLUMN owner_id BIGINT REFERENCES users(id);
