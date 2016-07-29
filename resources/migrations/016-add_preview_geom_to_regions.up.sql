ALTER TABLE regions
ADD COLUMN preview_geom geometry(MultiPolygon,4326);

DO $$
  BEGIN
    PERFORM calculate_regions_previews();
  END;
$$
