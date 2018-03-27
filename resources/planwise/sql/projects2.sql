-- :name db-create-project! :<! :1
INSERT INTO projects2
  ("owner-id", name, config, "dataset-id")
  VALUES (:owner-id, :name, NULL, NULL)
  RETURNING id;

-- :name db-update-project :!
UPDATE projects2
  SET name = :name, config = :config,
      "dataset-id" = :dataset-id,
      "region-id" = :region-id,
      "population-source-id" = :population-source-id
  WHERE id = :id;

-- :name db-get-project :? :1
SELECT projects2.*, datasets2."coverage-algorithm"
  FROM projects2
  LEFT JOIN datasets2 ON projects2."dataset-id" = datasets2.id
  WHERE projects2.id = :id

-- :name db-list-projects :?
SELECT id, name, state FROM projects2
    WHERE "owner-id" = :owner-id;

-- :name db-update-state-project :!
UPDATE projects2
  SET state = :state
  WHERE id = :id;
