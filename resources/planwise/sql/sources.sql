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
    VALUES (:name, 'points', 'not implemented', '', :owner-id)
    RETURNING id;

-- :name db-find-source-set :? :1
SELECT id, name, type
  FROM source_set
  WHERE id = :id AND "owner-id" = :owner-id;

-- :name db-create-source! :<! :1
INSERT INTO sources
    ("set_id", name, type, "the_geom", "quantity")
    VALUES (1, :name, :type, ST_SetSRID(ST_MakePoint(:lon, :lat), 4326), :quantity)
    RETURNING id;
