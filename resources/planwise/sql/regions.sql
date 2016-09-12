-- :name select-regions :? :*
SELECT id, country, name, admin_level AS "admin-level", ST_AsGeoJSON(ST_Envelope(the_geom)) AS "bbox", total_population AS "total-population"
FROM regions
ORDER BY admin_level DESC, name DESC;

-- :name select-regions-with-preview-given-ids :? :*
SELECT id, country, name, admin_level, ST_AsGeoJSON(preview_geom) AS "preview-geojson", ST_AsGeoJSON(ST_Envelope(the_geom)) as bbox, total_population AS "total-population"
FROM regions
WHERE id IN (:v*:ids);

-- :name select-regions-with-geo-given-ids :? :*
SELECT id, country, name, admin_level, ST_AsGeoJSON(preview_geom) AS "preview-geojson", ST_AsGeoJSON(ST_Simplify(the_geom, :simplify), 15, 3) as geojson, ST_AsGeoJSON(ST_Envelope(the_geom)) as bbox, total_population AS "total-population"
FROM regions
WHERE id IN (:v*:ids);

-- :name select-region :? :1
SELECT id,
country,
name,
admin_level AS "admin-level",
total_population AS "total-population",
max_population AS "max-population",
raster_pixel_area AS "raster-pixel-area"
FROM regions
WHERE id = :id;
