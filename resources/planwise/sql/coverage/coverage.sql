-- :name db-inside-geometry :? :1
SELECT ST_Within(ST_SetSRID(ST_MakePoint(:lon,:lat),4326), :geom) AS cond;

-- :name db-get-max-distance :? :1
SELECT ST_MaxDistance(:geom, :geom) AS maxdist;

-- :name db-intersected-coverage-region :? :1
SELECT
    St_AsGEOJSON(St_Intersection(St_MakeValid(:geom), regions.the_geom)) AS geom
    FROM regions
    WHERE regions.id = :region-id;


-- :name db-select-context :? :1
-- :doc Retrieve a coverage context
SELECT *
  FROM coverage_contexts
 WHERE cid = :cid;

-- :name db-insert-context! :! :1
-- :doc Inserts a new coverage context
INSERT
 INTO coverage_contexts (cid, region_id, options)
VALUES
 (:cid, :region-id, :options);

-- :name db-delete-context! :!
-- :doc Deletes a coverage context and associated coverages (by cascade)
DELETE FROM coverage_contexts WHERE cid = :cid;

-- :name db-check-coverage :? :1
-- :doc Checks if a coverage already exists and if it does, returns the distance
--  from the saved location to the given as parameter (used to check if the
--  geographical location changed)
SELECT
  lid, ST_Distance(location, :location) AS distance
  FROM coverages
 WHERE context_id = :context-id
   AND lid = :lid;

-- :name db-check-inside-region :? :1
-- :doc Checks whether a given geometry is inside a region
SELECT
  ST_Contains(regions.the_geom, :location) AS inside
  FROM regions
         INNER JOIN coverage_contexts
             ON regions.id = coverage_contexts.region_id
 WHERE coverage_contexts.id = :context-id;

-- :name db-upsert-coverage! :!
-- :doc Inserts (or updates) a coverage with the polygon and raster path
INSERT
  INTO coverages (context_id, lid, location, coverage, raster_file)
VALUES
 (:context-id, :lid, :location, ST_Multi(:coverage), :raster-file)
 ON CONFLICT (context_id, lid) DO
 UPDATE SET location = :location, coverage = ST_Multi(:coverage), raster_file = :raster-file;

-- :name db-clip-polygon :? :1
-- :doc Clips a polygon using a region as the cutline
SELECT
  ST_Intersection(ST_MakeValid(:polygon), regions.the_geom) AS "clipped-polygon"
  FROM regions
 WHERE regions.id = :region-id;





-- :name db-select-coverages :?
SELECT
  id, location, coverage, raster_file
  FROM coverages
 WHERE context_id = :context-id
  /*~ (when (:lids params) */
       AND lid IN (:v*:lids)
  /*~ ) ~*/
       ;
