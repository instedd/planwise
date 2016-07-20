-- :name insert-facility! :! :n
INSERT INTO facilities
    (id, name, lat, lon, type, the_geom)
    VALUES (:id, :name, :lat, :lon, :type, ST_SetSRID(ST_MakePoint(:lon, :lat), 4326));

-- :name delete-facilities! :!
DELETE FROM facilities;

-- :name select-facilities :?
SELECT
    id, name, lat, lon
FROM facilities
ORDER BY name;

-- :name facilities-by-criteria :? :*
SELECT
  id, name, type, lat, lon
FROM facilities
WHERE type IN (:v*:types)
/*~ (if (:region params) */
  AND ST_Contains((SELECT the_geom FROM regions WHERE id = :region LIMIT 1), facilities.the_geom)
/*~ ) ~*/
ORDER BY name;

-- :name count-facilities-in-region* :? :1
SELECT
  COUNT(*)
FROM facilities
WHERE ST_Contains(
  (SELECT the_geom FROM regions WHERE id = :region LIMIT 1),
  facilities.the_geom);

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
