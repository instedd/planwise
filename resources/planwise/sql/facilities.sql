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
/*~ (if (:types params) (if (empty? (:types params)) */
  AND 1=0
/*~*/
  AND facilities.type_id IN (:v*:types)
/*~ )) ~*/
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

-- :name count-facilities-by-criteria :? :1
SELECT
  COUNT(*)
FROM facilities
WHERE 1=1
:snip:criteria ;

-- :name facilities-with-isochrones :?
SELECT
  facilities.id AS id, facilities.name AS name, facilities.lat AS lat, facilities.lon AS lon,
  fp.id AS "polygon-id", ST_AsGeoJSON(ST_Simplify(fp.the_geom, :simplify)) AS isochrone,
  fp.population AS "population", fp.area AS "area"
  /*~ (if (:region params) */
  , fpr.population AS "population-in-region", fpr.area AS "area-in-region"
  /*~ ) ~*/
FROM facilities
  INNER JOIN facility_types ON facilities.type_id = facility_types.id
  LEFT OUTER JOIN facilities_polygons fp ON fp.facility_id = facilities.id AND fp.threshold = :threshold AND fp.method = :algorithm
  /*~ (if (:region params) */
  LEFT OUTER JOIN facilities_polygons_regions fpr ON fpr.region_id = :region AND fpr.facility_polygon_id = fp.id
  /*~ ) ~*/
WHERE 1=1 :snip:criteria ;

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

-- :name calculate-facility-isochrones! :<!
SELECT process_facility_isochrones(:id, :method, :start::integer, :end::integer, :step::integer);

-- :name select-facilities-polygons-regions-for-facility :?
SELECT fpr.facility_polygon_id AS "facility-polygon-id",
       fpr.region_id AS "region-id",
       fpr.area AS "area"
FROM facilities_polygons_regions AS fpr INNER JOIN facilities_polygons AS fp
  ON fpr.facility_polygon_id = fp.id
WHERE fp.facility_id = :facility-id;

-- :name set-facility-polygon-region-population! :!
UPDATE facilities_polygons_regions
SET population = :population
WHERE facility_polygon_id = :facility-polygon-id
  AND region_id = :region-id;

-- :name select-facilities-polygons-for-facility :?
SELECT fp.id AS "facility-polygon-id"
FROM facilities_polygons AS fp
WHERE fp.facility_id = :facility-id;

-- :name set-facility-polygon-population! :!
UPDATE facilities_polygons
SET population = :population
WHERE id = :facility-polygon-id;
