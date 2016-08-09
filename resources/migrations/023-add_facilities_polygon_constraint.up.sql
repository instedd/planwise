ALTER TABLE facilities_polygons
ADD CONSTRAINT facilities_polygons_facility_id_fkey
  FOREIGN KEY (facility_id)
  REFERENCES facilities(id)
  ON DELETE CASCADE;
