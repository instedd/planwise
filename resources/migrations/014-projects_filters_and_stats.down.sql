ALTER TABLE projects ADD COLUMN facilities_count integer;
ALTER TABLE projects DROP COLUMN stats;
ALTER TABLE projects DROP COLUMN filters;
