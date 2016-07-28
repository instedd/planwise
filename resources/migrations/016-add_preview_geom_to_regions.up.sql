ALTER TABLE regions
ADD COLUMN preview_geom geometry(MultiPolygon,4326);

SELECT calculate_regions_previews();
