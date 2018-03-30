-- :name db-create-project! :<! :1
INSERT INTO projects2
  ("owner-id", name, config, "dataset-id")
  VALUES (:owner-id, :name, NULL, NULL)
  RETURNING id;

-- :name db-update-project :!
UPDATE projects2
  SET name = :name, config = :config, "dataset-id" = :dataset-id,
      "region-id" = :region-id, "coverage-algorithm" = :coverage-algorithm
  WHERE id = :id;

-- :name db-get-project :?
SELECT * FROM projects2
    WHERE id = :id;

-- :name db-list-projects :?
SELECT id, name FROM projects2
    WHERE "owner-id" = :owner-id;

-- :name db-coverage-algorithm :<! :1
SELECT datasets2."coverage-algorithm"
    FROM datasets2, projects2
    WHERE projects2."dataset-id" = datasets2.id;
