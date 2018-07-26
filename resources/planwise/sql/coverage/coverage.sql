-- :name db-as-geojson :? :1
SELECT ST_AsGeoJSON(:geom) AS geom;
