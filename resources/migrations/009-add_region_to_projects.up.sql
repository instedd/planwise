ALTER TABLE projects ADD COLUMN region_id BIGINT NULL REFERENCES regions(id);
