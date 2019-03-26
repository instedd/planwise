CREATE EXTENSION IF NOT EXISTS postgis;
CREATE EXTENSION IF NOT EXISTS pgrouting;

CREATE OR REPLACE FUNCTION calculate_regions_previews()
RETURNS void AS $$
DECLARE
  r_id INTEGER;
BEGIN
  FOR r_id IN SELECT r.id FROM regions r WHERE r.preview_geom IS NULL LOOP
    PERFORM calculate_region_preview(r_id);
  END LOOP;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION calculate_region_preview(region_id INTEGER)
RETURNS INTEGER AS $$
DECLARE
  f_geom geometry(MultiPolygon,4326);
  simplify FLOAT;
  simplify_a FLOAT;
  simplify_b FLOAT;
  target INTEGER;
  current INTEGER;
  iter INTEGER;
BEGIN
  simplify_a := 0;
  simplify_b := 1;
  target := 3600;
  iter := 0;

  WHILE (iter = 0 OR (ABS(target - current) > 20 AND iter < 20)) LOOP
    simplify := (simplify_a + simplify_b) / 2;
    iter := iter + 1;

    SELECT LENGTH(ST_AsGeoJSON(ST_SimplifyPreserveTopology(the_geom, simplify), 15, 3))
    FROM regions WHERE id = region_id
    INTO current;

    IF current > target THEN
      simplify_a := simplify;
    ELSE
      simplify_b := simplify;
    END IF;
  END LOOP;

  -- FIXME: ST_SimplifyPreserveTopology doesn't always simplify and reduce the size of the resulting GeoJSON
  -- OTOH, ST_Simplify will produce invalid geometries sometimes (Dire Dawa in Ethiopia)
  UPDATE regions SET preview_geom = ST_Multi(ST_SimplifyPreserveTopology(the_geom, simplify)) WHERE id = region_id;
  RETURN current;
END;
$$ LANGUAGE plpgsql;
