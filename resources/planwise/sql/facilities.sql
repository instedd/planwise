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

-- :snip criteria-snip
/*~ (if (:types params) */
  AND facility_types.name IN (:v*:types)
/*~ ) ~*/
/*~ (if (:region params) */
  AND facilities.the_geom @ (SELECT the_geom FROM regions WHERE id = :region LIMIT 1)
  AND ST_Contains((SELECT the_geom FROM regions WHERE id = :region LIMIT 1), facilities.the_geom)
/*~ ) ~*/

-- :name facilities-by-criteria :? :*
SELECT
facilities.id as id, facilities.name as name, facility_types.name as type, lat, lon
FROM facilities
INNER JOIN facility_types ON facility_types.id = facilities.type_id
WHERE 1=1
:snip:criteria ;

-- :name facilities-with-isochrones :?
SELECT
  facilities.id AS id, facilities.name AS name, facilities.lat AS lat, facilities.lon AS lon,
  ST_AsGeoJSON(ST_Simplify(fp.the_geom, :simplify)) AS isochrone
FROM facilities_polygons fp
  INNER JOIN facilities ON fp.facility_id = facilities.id
WHERE fp.threshold = :threshold
  AND fp.method = :algorithm
  :snip:criteria ;

-- :name isochrone-for-facilities :? :1
SELECT
  ST_AsGeoJSON(ST_Union(the_geom))
FROM facilities_polygons
WHERE threshold = :threshold
AND method = 'alpha-shape';

-- :name select-types :?
SELECT id, name
FROM facility_types;

-- :name delete-types! :!
DELETE FROM facility_types;

-- :name insert-type! :<! :1
INSERT INTO facility_types (name)
VALUES (:name)
RETURNING id;
