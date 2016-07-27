ALTER TABLE projects ADD COLUMN filters text;
ALTER TABLE projects ADD COLUMN stats text;
ALTER TABLE projects DROP COLUMN facilities_count;
