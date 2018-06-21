-- :name db-list-sources :?
SELECT id, name, type
  FROM source_set
  WHERE "owner-id" = :owner-id OR "owner-id" IS NULL;

-- :name db-create-source-set! :<! :1
INSERT INTO source_set
    (name, type, unit, raster_file)
    VALUES (:name, 'points', NULL, NULL)
    RETURNING id;
