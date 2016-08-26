-- :name select-datasets-for-user :?
SELECT
  id,
  name,
  description,
  (SELECT COUNT(*) FROM facilities WHERE facilities.dataset_id = datasets.id) AS "facility-count",
  (SELECT COUNT(*) FROM projects WHERE projects.dataset_id = datasets.id) AS "project-count",
  import_result AS "import-result",
  collection_id AS "collection-id",
  owner_id AS "owner-id"
FROM datasets
WHERE owner_id = :user-id
ORDER BY id ASC;

-- :name insert-dataset! :<! :1
INSERT INTO datasets
  (name, description, owner_id, collection_id, import_mappings, import_result)
VALUES
  (:name, :description, :owner-id, :collection-id, :mappings, :import-result)
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
(for [field [:description] :when (some? (field params))]
(str (name field) " = :" (name field))))
~*/
WHERE datasets.id = :id;

-- :name delete-dataset! :! :n
DELETE FROM datasets
WHERE datasets.id = :id;
