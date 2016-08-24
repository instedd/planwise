-- :name select-datasets-for-user :?
SELECT
  id,
  name,
  description,
  facility_count AS "facility-count",
  collection_id AS "collection-id",
  owner_id AS "owner-id"
FROM datasets
WHERE owner_id = :user-id
ORDER BY id ASC;

-- :name insert-dataset! :<! :1
INSERT INTO datasets
  (name, description, owner_id, collection_id, import_mappings, facility_count)
VALUES
  (:name, :description, :owner-id, :collection-id, :mappings, 0)
RETURNING id;
