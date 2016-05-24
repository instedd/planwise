-- :name get-nearest-node :? :1
-- :doc Retrieve the nearest node (with the distance) to the given lat,lon
--      coordinates
SELECT
  id,
  ST_AsGeoJSON(the_geom) AS point,
  ST_Distance(the_geom, ST_SetSRID(ST_MakePoint(:lon, :lat), 4326)) AS distance
FROM ways_vertices_pgr
ORDER BY the_geom <-> ST_SetSRID(ST_MakePoint(:lon, :lat), 4326)
LIMIT 1


-- FIXME: pass the query parameters as JDBC parameters instead of building the
-- query by interpolating

-- :name isochrone-for-node :? :1
SELECT
  ST_AsGeoJSON(
    pgr_pointsAsPolygon($$SELECT
                            id::integer, ST_X(the_geom)::float AS x, ST_Y(the_geom)::float AS y
                          FROM ways_vertices_pgr
                          WHERE id IN (SELECT node
                                       FROM pgr_drivingDistance(''SELECT gid AS id, source, target, cost FROM ways'',
                                                                $$ || :node-id || $$, $$ || :distance || $$, false))
                        $$, 0)) AS poly

