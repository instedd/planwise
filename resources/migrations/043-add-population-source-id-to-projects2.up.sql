ALTER TABLE projects2 ADD COLUMN "population-source-id" BIGINT REFERENCES population_sources(id) DEFAULT NULL;
