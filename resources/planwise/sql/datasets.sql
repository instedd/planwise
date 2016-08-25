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

-- :name select-dataset :? :1
SELECT
  id,
  name,
  description,
  collection_id AS "collection-id",
  import_mappings AS "mappings",
  owner_id AS "owner-id"
FROM datasets
WHERE id = :id;

-- :name update-dataset* :! :n
/* :require [clojure.string :as string] */
UPDATE datasets SET
/*~
(string/join ","
(for [field [:facility-count :description] :when (some? (field params))]
(str (name field) " = :" (name field))))
~*/
WHERE datasets.id = :id;

-- :name delete-dataset! :! :n
DELETE FROM datasets
WHERE datasets.id = :id;
