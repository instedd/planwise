DROP TABLE IF EXISTS project_shares;

ALTER TABLE projects
DROP COLUMN IF EXISTS share_token;
