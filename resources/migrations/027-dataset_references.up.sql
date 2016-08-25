ALTER TABLE facility_types
      ADD COLUMN dataset_id BIGINT NOT NULL
          REFERENCES datasets(id) ON DELETE CASCADE;

ALTER TABLE facilities
      DROP CONSTRAINT IF EXISTS facilities_pkey CASCADE;

ALTER TABLE facilities
      RENAME COLUMN id TO site_id;

ALTER TABLE facilities
      ADD COLUMN id SERIAL PRIMARY KEY;

ALTER TABLE facilities
      ADD COLUMN dataset_id BIGINT NOT NULL
          REFERENCES datasets(id) ON DELETE CASCADE;

ALTER TABLE facilities_polygons
      ADD CONSTRAINT facilities_polygons_facility_id_pkey
      FOREIGN KEY (facility_id)
      REFERENCES facilities(id) ON DELETE CASCADE;

ALTER TABLE projects
      ADD COLUMN dataset_id BIGINT NOT NULL
      REFERENCES datasets(id) ON DELETE RESTRICT;
