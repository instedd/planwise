CREATE TABLE IF NOT EXISTS project_shares (
  project_id BIGINT REFERENCES projects(id) ON DELETE CASCADE,
  user_id BIGINT REFERENCES users(id) ON DELETE CASCADE,
  PRIMARY KEY (project_id, user_id)
);

CREATE INDEX project_shares_project_id_idx ON project_shares(project_id);
CREATE INDEX project_shares_user_id_idx ON project_shares(user_id);

ALTER TABLE projects
ADD COLUMN share_token VARCHAR(60) NULL;

CREATE EXTENSION IF NOT EXISTS pgcrypto;

UPDATE projects SET share_token = gen_random_uuid()
WHERE share_token IS NULL;

CREATE INDEX proje_sharing_token_idx ON projects(share_token);
