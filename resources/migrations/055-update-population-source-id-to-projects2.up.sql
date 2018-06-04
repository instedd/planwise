ALTER TABLE projects2
  DROP COLUMN IF EXISTS "population-source-id",
  ADD COLUMN "population-source-id" BIGINT REFERENCES sources_set(id) DEFAULT NULL;
