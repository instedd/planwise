-- :name insert-facility! :! :n
INSERT INTO facilities
    (id, name, lat, lon, the_geom)
    VALUES (:id, :name, :lat, :lon, ST_SetSRID(ST_MakePoint(:lon, :lat), 4326));

-- :name delete-facilities! :!
DELETE FROM facilities;

-- :name select-facilities :?
SELECT
    id, name, lat, lon
FROM facilities
WHERE name LIKE '%HOSPITAL%'
ORDER BY name;

-- :name facilities-with-isochrones :?
SELECT
  fp.facility_id, ST_AsGeoJSON(fp.the_geom) AS isochrone, ST_AsGeoJSON(f.the_geom) AS point
FROM facilities_polygons fp
INNER JOIN facilities f
ON fp.facility_id = f.id
WHERE fp.threshold = :threshold;

-- :name isochrone-for-facilities :? :1
SELECT
  ST_AsGeoJSON(ST_Union(the_geom))
FROM facilities_polygons
WHERE threshold = :threshold;
