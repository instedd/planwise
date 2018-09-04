-- :name find-country-region-with-point :? :1
-- Countries are admin_level 0 (using gadm data) and the load-friction-raster
-- script will only clip the global raster file to the regions delimited by them
SELECT id FROM regions WHERE "admin_level" = 0 AND ST_Contains("the_geom", :point) LIMIT 1;
