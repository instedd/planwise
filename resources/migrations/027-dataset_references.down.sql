ALTER TABLE facilities DROP COLUMN id CASCADE;
ALTER TABLE facilities RENAME COLUMN site_id TO id;
ALTER TABLE facilities DROP COLUMN IF EXISTS dataset_id;
ALTER TABLE facility_types DROP COLUMN IF EXISTS dataset_id;
ALTER TABLE projects DROP COLUMN IF EXISTS dataset_id;
