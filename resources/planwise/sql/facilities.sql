-- :name insert-facility! :! :n
INSERT INTO facilities
    (id, name, lat, lon, type_id, the_geom)
    VALUES (:id, :name, :lat, :lon, :type_id, ST_SetSRID(ST_MakePoint(:lon, :lat), 4326));

-- :name delete-facilities! :!
DELETE FROM facilities;

-- :name select-facilities :?
SELECT
    id, name, lat, lon
FROM facilities
ORDER BY name;

-- :name facilities-from-types :? :*
SELECT
f.id as id, f.name as name, t.name as type, lat, lon
FROM facilities f
INNER JOIN facility_types t ON t.id = f.type_id
WHERE t.name IN (:v*:types)
ORDER BY f.name;

-- :name select-types :?
SELECT name
FROM facility_types;

-- :name facilities-with-isochrones :?
SELECT
  fp.facility_id, f.name, f.lat, f.lon, ST_AsGeoJSON(ST_Simplify(fp.the_geom, :simplify)) AS isochrone
FROM facilities_polygons fp
INNER JOIN facilities f
ON fp.facility_id = f.id
WHERE fp.threshold = :threshold
AND fp.method = :method;

-- :name isochrone-for-facilities :? :1
SELECT
  ST_AsGeoJSON(ST_Union(the_geom))
FROM facilities_polygons
WHERE threshold = :threshold
AND method = 'alpha-shape';
