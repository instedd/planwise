ALTER TABLE facilities_polygons DROP COLUMN starting_node;

DROP FUNCTION IF EXISTS process_facility_isochrones(bigint, varchar, integer, integer, integer);
