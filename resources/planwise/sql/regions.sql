-- :name select-regions :? :*
SELECT id, country, name, admin_level, ST_AsGeoJSON(ST_Envelope(the_geom)) as bbox, total_population
FROM regions
ORDER BY admin_level DESC, name DESC;

-- :name select-regions-with-preview-given-ids :? :*
SELECT id, country, name, admin_level, ST_AsGeoJSON(preview_geom) as preview_geojson, ST_AsGeoJSON(ST_Envelope(the_geom)) as bbox, total_population
FROM regions
WHERE id IN (:v*:ids);

-- :name select-regions-with-geo-given-ids :? :*
SELECT id, country, name, admin_level, ST_AsGeoJSON(preview_geom) as preview_geojson, ST_AsGeoJSON(ST_Simplify(the_geom, :simplify), 15, 3) as geojson, ST_AsGeoJSON(ST_Envelope(the_geom)) as bbox, total_population
FROM regions
WHERE id IN (:v*:ids);
