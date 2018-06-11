ALTER TABLE projects2
  DROP COLUMN IF EXISTS "source-set-id",
  ADD COLUMN "population-source-id" BIGINT REFERENCES population_sources(id) DEFAULT NULL;
