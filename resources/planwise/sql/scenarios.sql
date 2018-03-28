-- :name db-list-scenarios :?
SELECT id, name, investment, "demand-coverage"
FROM scenarios
WHERE "project-id" = :project-id

-- :name db-find-scenario :? :1
SELECT *
FROM scenarios
WHERE id = :id

-- :name db-create-scenario! :<! :1
INSERT INTO scenarios
  (name, "project-id", investment, "demand-coverage", changeset)
VALUES
  (:name, :project-id, :investment, NULL, :changeset)
RETURNING id;

-- :name db-update-scenario! :! :1
UPDATE scenarios
  SET name = :name, investment = :investment,
  "demand-coverage" = :demand-coverage, changeset = :changeset
WHERE
  id = :id
