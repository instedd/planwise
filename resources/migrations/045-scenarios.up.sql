CREATE TABLE IF NOT EXISTS scenarios (
  id BIGSERIAL PRIMARY KEY,
  name VARCHAR(255) NOT NULL,
  "project-id" BIGINT NOT NULL REFERENCES projects2(id),
  label VARCHAR(255) NULL,
  investment NUMERIC(12, 2) NULL,
  "demand-coverage" BIGINT NULL,
  changeset TEXT NOT NULL
);
