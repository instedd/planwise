DROP TABLE IF EXISTS facilities_polygons;

DROP FUNCTION IF EXISTS closest_node(geometry(point, 4326));

DROP FUNCTION IF EXISTS calculate_isochrones(varchar, integer, integer, integer);

DROP FUNCTION IF EXISTS cache_ways_buffers(float);
