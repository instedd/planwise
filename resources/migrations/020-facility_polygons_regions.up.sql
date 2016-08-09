CREATE TABLE facilities_polygons_regions (
       facility_polygon_id BIGINT REFERENCES facilities_polygons(id) ON DELETE CASCADE,
       region_id INT REFERENCES regions(id) ON DELETE CASCADE,
       area FLOAT,
       population INT);

CREATE INDEX facilities_polygons_regions_facility_polygon_id
ON facilities_polygons_regions(facility_polygon_id);

CREATE INDEX facilities_polygons_regions_region_id
ON facilities_polygons_regions(region_id);

INSERT INTO facilities_polygons_regions(facility_polygon_id, region_id, area)
SELECT fp.id, r.id, ST_Area(ST_Intersection(fp.the_geom, r.the_geom))
FROM regions AS r INNER JOIN facilities_polygons AS fp
  ON ST_Intersects(fp.the_geom, r.the_geom);
