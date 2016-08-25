ALTER TABLE facilities
ADD COLUMN processing_status VARCHAR;

UPDATE facilities AS f
SET processing_status =
  CASE
    WHEN EXISTS (SELECT 1 FROM facilities_polygons fp WHERE fp.facility_id = f.id LIMIT 1)
      THEN 'ok'
    ELSE
      'no-road-network'
  END
WHERE f.processing_status IS NULL;

-- The following version indeed calculates if the facility is too far from the
-- road network, but takes too long to run. So we use the version above, that
-- assumes that, if we are running migrations, there are no facilities pending
-- processing, so they have polygons iif they are close to the road network.

-- UPDATE facilities AS f
-- SET processing_status =
--   CASE
--     WHEN ST_Distance(
--       ST_GeogFromWKB(f.the_geom),
--       ST_GeogFromWKB((
--         SELECT wvp.the_geom
--         FROM ways_vertices_pgr AS wvp
--         WHERE wvp.id = closest_node(f.the_geom)
--         LIMIT 1
--       ))) > 1000 THEN 'no-road-network'
--     ELSE 'ok'
--   END
-- WHERE f.processing_status IS NULL;
