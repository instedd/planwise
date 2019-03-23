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
SELECT *
  FROM coverage_contexts
 WHERE cid = :cid;

-- :name db-insert-context! :! :1
INSERT
 INTO coverage_contexts (cid, region_id, options)
VALUES
 (:cid, :region-id, :options);

-- :name db-delete-context! :!
DELETE FROM coverage_contexts WHERE cid = :cid;

-- :name db-check-coverage :? :1
SELECT
  lid, ST_Distance(location, :location) AS distance
  FROM coverages
 WHERE context_id = :context-id
   AND lid = :lid;

-- :name db-check-inside-region :? :1
SELECT
  ST_Contains(regions.the_geom, :location) AS inside
  FROM regions
         INNER JOIN coverage_contexts
             ON regions.id = coverage_contexts.region_id
 WHERE coverage_contexts.id = :context-id;

-- :name db-upsert-coverage! :!
INSERT
  INTO coverages (context_id, lid, location, coverage, raster_path)
VALUES
 (:context-id, :lid, :location, :coverage, :raster-path)
 ON CONFLICT (context_id, lid) DO
 UPDATE SET location = :location, coverage = :coverage, raster_path = :raster-path;

-- :name db-select-coverages :?
SELECT
  id, location, coverage, raster_path AS "raster-path"
  FROM coverages
 WHERE context_id = :context-id
  /*~ (when (:lids params) */
       AND lid IN (:v*:lids)
  /*~ ) ~*/
       ;
