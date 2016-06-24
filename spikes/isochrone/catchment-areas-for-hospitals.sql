-- Create a table to hold the results
CREATE TABLE catchment (
       facility_id INTEGER,
       node_id INTEGER,
       the_geom GEOMETRY(polygon,4326)
);

-- Insert facilities and the nearest node in the topology network
INSERT INTO catchment
       (facility_id, node_id)
SELECT f.id, (SELECT w.id
              FROM ways_vertices_pgr w
              ORDER BY w.the_geom <-> f.the_geom LIMIT 1)
FROM facilities f
WHERE f.name LIKE '%HOSPITAL%';

-- Remove those facilities which yield less than 3 nodes for the catchment
-- polygon (since these cannot generate a proper polygon)
DELETE FROM catchment
WHERE (SELECT COUNT(*)
       FROM pgr_drivingDistance('SELECT gid AS id, source, target, cost FROM ways', node_id, 0.5, false)) < 3;

-- Calculate the catchment polygons for each facility
UPDATE catchment
SET the_geom = (SELECT ST_SetSRID(pgr_pointsAsPolygon(
                       $$SELECT id::integer,
                                ST_X(the_geom)::float AS x,
                                ST_Y(the_geom)::float AS y
                         FROM ways_vertices_pgr
                         WHERE id IN (
                               SELECT node
                               FROM pgr_drivingDistance(
                                    ''SELECT gid AS id, source, target, cost
                                      FROM ways'',
                       $$ || node_id || $$, 0.5, false))$$, 0), 4326));

-- Return the union of all polygons
SELECT ST_AsGeoJSON(ST_Union(the_geom)) FROM catchment;
