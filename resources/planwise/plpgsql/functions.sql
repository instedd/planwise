CREATE EXTENSION IF NOT EXISTS postgis;
CREATE EXTENSION IF NOT EXISTS pgrouting;

-- Populate the ways_nodes table with all the vertices in each way
-- This *will* produce duplicate nodes as it is.
CREATE OR REPLACE FUNCTION populate_ways_nodes()
RETURNS void AS $$
BEGIN
  TRUNCATE TABLE ways_nodes;
  INSERT INTO ways_nodes (gid, lon, lat)
    SELECT
      gid,
      ST_x(ST_PointN(the_geom, g.generate_series)) AS lon,
      ST_y(ST_PointN(the_geom, g.generate_series)) AS lat
    FROM
      ways,
      (SELECT generate_series(1, (SELECT MAX(ST_NPoints(the_geom)) FROM ways))) g
    WHERE g.generate_series <= ST_NPoints(the_geom);
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION apply_traffic_factor(factor float)
RETURNS void AS $$
BEGIN
UPDATE ways
SET cost_s = length_m / (maxspeed_forward * 5 / 18) * factor;
END;
$$ LANGUAGE plpgsql;
