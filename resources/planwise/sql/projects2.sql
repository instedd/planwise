-- :name db-create-project! :<! :1
INSERT INTO projects2
  ("owner-id", name, config, "dataset-id", state, "deleted-at")
  VALUES (:owner-id, :name, NULL, NULL, :state, NULL)
  RETURNING id;

-- :name db-update-project :!
UPDATE projects2
  SET name = :name, config = :config,
      "dataset-id" = :dataset-id,
      "region-id" = :region-id,
      "population-source-id" = :population-source-id
  WHERE id = :id;

-- :name db-get-project :? :1
SELECT projects2.id, projects2."owner-id", projects2.name, projects2.config,
       projects2."dataset-id", projects2."dataset-version", projects2.state, projects2."population-source-id",
       projects2."engine-config", projects2."region-id", datasets2."coverage-algorithm"
  FROM projects2
  LEFT JOIN datasets2 ON projects2."dataset-id" = datasets2.id
  WHERE projects2.id = :id;

-- :name db-list-projects :?
SELECT id, name, "region-id", state FROM projects2
    WHERE "deleted-at" is NULL
    AND "owner-id" = :owner-id;

-- :name db-update-state-project :!
UPDATE projects2
  SET state = :state
  WHERE id = :id;

-- :name db-start-project! :!
UPDATE "projects2" AS p2
  SET "state" = 'started',
      "dataset-version" = (SELECT "last-version" FROM "datasets2" d2 WHERE d2."id" = p2."dataset-id")
  WHERE "id" = :id;

-- :name db-reset-project! :!
UPDATE "projects2" AS p2
  SET "state" = 'draft'
  WHERE "id" = :id;

-- :name db-delete-project! :!
UPDATE projects2
  SET "deleted-at" = NOW()
  WHERE id = :id;
