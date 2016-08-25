-- :name insert-facility! :! :n
INSERT INTO facilities
    (dataset_id, site_id, name, lat, lon, type_id, the_geom)
    VALUES (:dataset-id, :site-id, :name, :lat, :lon, :type-id, ST_SetSRID(ST_MakePoint(:lon, :lat), 4326));

-- :name delete-facilities-in-dataset! :!
DELETE FROM facilities WHERE dataset_id = :dataset-id;

-- :name select-facilities-in-dataset :?
SELECT
    id, name, lat, lon
FROM facilities
WHERE dataset_id = :dataset-id
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

-- :name facilities-in-dataset-by-criteria :? :*
SELECT
  facilities.id as id, facilities.name as name, facility_types.name as type,
  facility_types.id as "type-id", lat, lon,
  facilities.processing_status AS "processing-status"
FROM facilities
INNER JOIN facility_types ON facility_types.id = facilities.type_id
WHERE facilities.dataset_id = :dataset-id
:snip:criteria ;

-- :name count-facilities-in-dataset-by-criteria :? :1
SELECT
  COUNT(*)
FROM facilities
WHERE facilities.dataset_id = :dataset-id
:snip:criteria ;

-- :name isochrones-for-dataset-in-bbox* :?
SELECT
  facilities.id AS "id",
  fp.id AS "polygon-id",
  /*~ (if (empty? (:excluding params)) */
  ST_AsGeoJSON(ST_Simplify(fp.the_geom, :simplify)) AS "isochrone"
  /*~*/
  CASE facilities.id IN (:v*:excluding)
    WHEN FALSE THEN ST_AsGeoJSON(ST_Simplify(fp.the_geom, :simplify))
    ELSE NULL
  END AS "isochrone"
  /*~ ) ~*/
FROM facilities
  INNER JOIN facility_types ON facilities.type_id = facility_types.id
  LEFT OUTER JOIN facilities_polygons fp ON fp.facility_id = facilities.id AND fp.threshold = :threshold AND fp.method = :algorithm
WHERE
  facilities.dataset_id = :dataset-id
  AND (fp.the_geom && ST_MakeEnvelope(:v*:bbox, 4326))
  :snip:criteria ;

-- :name select-types-in-dataset :?
SELECT id, name
FROM facility_types
WHERE dataset_id = :dataset-id;

-- :name delete-types-in-dataset! :!
DELETE FROM facility_types
WHERE dataset_id = :dataset-id;

-- :name insert-type! :<! :1
INSERT INTO facility_types (name, dataset_id)
VALUES (:name, :dataset-id)
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
