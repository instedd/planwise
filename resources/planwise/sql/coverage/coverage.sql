-- :name db-as-geojson :? :1
SELECT ST_AsGeoJSON(:geom) AS geom;

-- :name db-inside-geometry :? :1
SELECT (ST_Within(ST_SetSRID(ST_MakePoint(:lon,:lat),4326), :geom)) AS cond;