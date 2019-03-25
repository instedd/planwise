-- :name db-list-sources :?
SELECT id,
       name,
       type,
       (SELECT COUNT(*) FROM sources WHERE sources.set_id = source_set.id) AS "sources-count"
  FROM source_set
  WHERE "owner-id" = :owner-id OR "owner-id" IS NULL;

-- :name db-create-source-set! :<! :1
INSERT INTO source_set
    (name, type, unit, raster_file, "owner-id")
    VALUES (:name, 'points', :unit, NULL, :owner-id)
    RETURNING id;

-- :name db-find-source-set :? :1
SELECT id,
       name,
       type,
       raster_file AS "raster-file",
       (SELECT COUNT(*) FROM sources WHERE sources.set_id = source_set.id) AS "sources-count"
  FROM source_set
  WHERE id = :id AND "owner-id" = :owner-id;

-- :name db-find-source-set-by-id :? :1
SELECT id,
       name,
       type,
       raster_file AS "raster-file",
       (SELECT COUNT(*) FROM sources WHERE sources.set_id = source_set.id) AS "sources-count"
  FROM source_set
  WHERE id = :id;

-- :name db-create-source! :<! :1
INSERT INTO sources
    ("set_id", name, type, "the_geom", "quantity")
    VALUES (:set-id, :name, :type, ST_SetSRID(ST_MakePoint(:lon, :lat), 4326), :quantity)
    RETURNING id;

-- :name db-list-sources-in-set :?
SELECT *,
       ST_X(the_geom) AS lon,
       ST_Y(the_geom) AS lat
  FROM
    sources
  WHERE
    set_id = :source-set-id;

-- :name db-get-sources-from-set-in-region :?
SELECT
  s.id,
  s.name,
  s.type,
  s.quantity,
  ST_X(s.the_geom) AS lon,
  ST_Y(s.the_geom) AS lat
  FROM
    sources s,
    regions r
  WHERE
    s."set_id" = :source-set-id
    AND r.id = :region-id
    AND ST_Contains(r."the_geom", s."the_geom");

-- :name db-enum-sources-under-coverage :?
SELECT
  s.id
  FROM
    sources AS s
  WHERE
    set_id = :source-set-id
    AND ST_Contains(:coverage-geom, the_geom);
