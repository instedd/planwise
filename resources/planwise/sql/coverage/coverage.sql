-- :name db-inside-geometry :? :1
SELECT ST_Within(ST_SetSRID(ST_MakePoint(:lon,:lat),4326), :geom) AS cond;

-- :name db-get-max-distance :? :1
SELECT ST_MaxDistance(:geom, :geom) AS maxdist;

-- :name db-intersected-coverage-region :? :1
SELECT
    St_AsGEOJSON(St_Intersection(St_MakeValid(:geom), regions.the_geom)) AS geom
    FROM regions
    WHERE regions.id = :region-id;


-- :name db-insert-context! :! :1
INSERT
 INTO coverage_contexts (id, options)
VALUES
 (:id, :options);

-- :name db-delete-context! :!
DELETE FROM coverage_contexts WHERE id = :id;

-- :name db-upsert-coverage! :!
INSERT
  INTO coverages (context_id, id, location, coverage, raster_path)
VALUES
 (:context-id, :id, :location, :coverage, :raster-path)
 ON CONFLICT DO
 UPDATE SET location = :location, coverage = :coverage, raster_path = :raster-path;

-- :name db-select-coverages :?
SELECT
  id, location, coverage, raster_path AS "raster-path"
  FROM coverages
 WHERE context_id = :context-id
  /*~ (when (:ids params) */
       AND id IN (:v*:ids)
  /*~ ) ~*/
       ;
