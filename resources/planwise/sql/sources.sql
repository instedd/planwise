-- :name db-list-sources :?
SELECT id, name, type
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
  WHERE id = :id;
