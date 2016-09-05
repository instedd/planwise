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
  import_result AS "import-result",
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
(for [[key field] [[:description :description] [:import-result :import_result]] :when (some? (key params))]
(str (name field) " = :" (name key))))
~*/
WHERE datasets.id = :id;

-- :name delete-dataset! :! :n
DELETE FROM datasets
WHERE datasets.id = :id;

-- :name count-accessible-projects-for-dataset :? :1
SELECT COUNT(*) AS "count"
FROM projects AS p
LEFT JOIN project_shares ps ON p.id = ps.project_id
WHERE p.dataset_id = :dataset-id
  AND (ps.user_id = :user-id OR p.owner_id = :user-id)
