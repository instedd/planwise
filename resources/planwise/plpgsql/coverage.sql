-- Computes the coverage polygon from a point using pgRouting and alpha shape
-- Parameters:
--   point: starting point
--   max_time_minutes: driving time in minutes

DROP FUNCTION IF EXISTS pgr_alpha_shape_coverage(geometry(point, 4326), INTEGER);

CREATE OR REPLACE FUNCTION pgr_alpha_shape_coverage(point geometry(point, 4326), max_time_minutes INTEGER)
RETURNS RECORD AS $$
DECLARE
  closest_node INTEGER;
  closest_node_geom geometry(point, 4326);
  buffer_length INTEGER;
  bounding_radius_meters FLOAT;
  threshold INTEGER;
  distance_threshold_meters FLOAT;
  polygon geometry(polygon, 4326);
  ret RECORD; -- (result_code, polygon?)
BEGIN
  CREATE TEMPORARY TABLE IF NOT EXISTS edges_agg_cost (
    gid INTEGER NOT NULL,
    agg_cost DOUBLE PRECISION,
    node INTEGER NOT NULL
  );

  closest_node := (SELECT closest_node(point));
  closest_node_geom := (SELECT the_geom FROM ways_vertices_pgr WHERE id = closest_node);
  threshold := max_time_minutes * 60;

  -- Maximum distance from the closest node in the road network to discard a facility
  -- This number was adjusted manually such that the ~90% of current facility dataset from Kenya is not discarded
  distance_threshold_meters := 5000;

  IF ST_Distance(ST_GeogFromWKB(point), ST_GeogFromWKB(closest_node_geom)) > distance_threshold_meters THEN
    SELECT 'no-road-network'::TEXT, NULL::geometry into ret;
    RETURN ret;
  END IF;

  -- Apply an upper bound to the reachable region to avoid retrieving all ways
  -- bound = area that can be covered traveling in a straight line at 120 km/h
  -- for the threshold time.
  bounding_radius_meters := (threshold / 3600.0) * 120 * 1000;

  INSERT INTO edges_agg_cost (
    SELECT e.edge, e.agg_cost, e.node
    FROM pgr_drivingdistance(
      'SELECT gid AS id, source, target, cost_s AS cost FROM ways WHERE the_geom @ (SELECT ST_Buffer(ST_GeomFromEWKT(''' || (SELECT ST_AsEWKT(point)) || ''')::geography, ' || bounding_radius_meters || ')::geometry)',
      -- 'SELECT gid AS id, source, target, cost_s AS cost FROM ways',
      closest_node,
      threshold,
      FALSE) e
  );

  -- This snippet adds edges that may be left out but should be included.
  -- Since pgr_drivingDistance uses the Dijkstra's algorithm and its corresponding
  -- spanning tree, if there are edges between two reachable nodes that do not
  -- belong to the shortest path to either of them then that edge won't be included
  -- but it should if traversing it doesn't exceeds the maximum threshold.
  INSERT INTO edges_agg_cost (
    SELECT w.gid, least(e_source.agg_cost, e_target.agg_cost) + w.cost AS agg_cost, w.target
    FROM ways w
    JOIN edges_agg_cost e_source ON e_source.node = w.source
    JOIN edges_agg_cost e_target ON e_target.node = w.target
    WHERE w.cost + least(e_source.agg_cost, e_target.agg_cost) < threshold
  );

  -- Buffer in meters to apply around the alpha shape polygon
  buffer_length := 300;

  BEGIN
    polygon := (SELECT ST_Buffer(ST_GeogFromWKB(ST_SetSRID(patched_pointsAsPolygon('SELECT id::integer, lon::float AS x, lat::float AS y FROM ways_nodes WHERE gid IN (SELECT gid FROM edges_agg_cost WHERE agg_cost < ' || threshold || ') UNION SELECT -1 AS id, ' || ST_X(point) || ' AS x, ' || ST_Y(point) || ' AS y'), 4326)), buffer_length)::geometry);
    SELECT 'ok'::TEXT, polygon INTO ret;
  EXCEPTION WHEN OTHERS THEN
    RAISE NOTICE 'Failed to calculate alpha shape';
    SELECT 'alpha-shape-failed'::TEXT, NULL::geometry INTO ret;
  END;

  DELETE FROM edges_agg_cost;

  -- Cannot drop the temp table when running the alpha shape algorithm because
  -- pgr holds a reference to it
  -- drop table edges_agg_cost;

  RETURN ret;
END;
$$ LANGUAGE plpgsql;
