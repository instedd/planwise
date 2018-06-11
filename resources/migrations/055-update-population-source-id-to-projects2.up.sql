ALTER TABLE projects2
  DROP COLUMN IF EXISTS "population-source-id",
  ADD COLUMN "source-set-id" BIGINT REFERENCES source_set(id) DEFAULT NULL;
