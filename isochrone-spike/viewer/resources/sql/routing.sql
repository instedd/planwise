SELECT
  id,
  ST_AsGeoJSON(the_geom) AS point,
  ST_Distance(the_geom, ST_SetSRID(ST_MakePoint(?, ?), 4326)) AS distance
FROM ways_vertices_pgr
ORDER BY the_geom <-> ST_SetSRID(ST_MakePoint(?, ?), 4326)
LIMIT 1


-- FIXME: pass the query parameters as JDBC parameters instead of building the
-- query by interpolating

SELECT ST_AsGeoJSON(pgr_pointsAsPolygon('SELECT id::integer, ST_X(the_geom)::float AS x, ST_Y(the_geom)::float AS y
                                         FROM ways_vertices_pgr
                                         WHERE id IN (SELECT node
                                                      FROM pgr_drivingDistance(''''SELECT gid AS id, source, target, to_cost AS cost
                                                                                   FROM ways'''', " id ", " distance "))', 0)) AS poly
