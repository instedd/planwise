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


-- :name isochrone-for-node-alpha-shape :? :1
-- :doc Calculate the approximate isochrone from the given node. The node must
--      exist in the created topology network (a corresponding record in
--      ways_vertices_pgr). The cost field for the edges is cost_s which measures the
--      cost to traverse the edge in seconds at the maximum permitted speed for the
--      highway type. Applies a buffer over the alpha shape.

SELECT
  ST_AsGeoJSON(ST_Buffer(
    pgr_pointsAsPolygon($$SELECT
                            id::integer, ST_X(the_geom)::float AS x, ST_Y(the_geom)::float AS y
                          FROM ways_vertices_pgr
                          WHERE id IN (SELECT node
                                       FROM pgr_drivingDistance(''SELECT gid AS id, source, target, cost_s AS cost FROM ways'',
                                                                $$ || :node-id || $$, $$ || :distance || $$, false))
                        $$, 0), 0.004)) AS poly

-- :name isochrone-for-node-buffer :? :1
-- :doc Calculate the approximate isochrone from the given node. The node must
--      exist in the created topology network (a corresponding record in
--      ways_vertices_pgr). The cost field for the edges is cost_s which measures the
--      cost to traverse the edge in seconds at the maximum permitted speed for the
--      highway type. Applies a buffer over the resulting edges.

SELECT
  ST_AsGeoJSON(ST_Union(ST_Buffer(w.the_geom, 0.004))) AS poly
FROM pgr_drivingDistance('SELECT gid AS id, source, target, cost_s AS cost FROM ways', :node-id::integer, :distance::float, false) e
JOIN ways w ON e.edge = w.gid;
