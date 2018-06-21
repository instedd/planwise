-- :name db-list-sources :?
SELECT id, name, type
  FROM source_set
  WHERE "owner-id" = :owner-id OR "owner-id" IS NULL;
