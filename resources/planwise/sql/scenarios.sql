-- :name db-list-scenarios :?
SELECT id, name, investment, "demand-coverage",
CASE
  WHEN name = 'Initial' THEN 'initial'
  WHEN rank() OVER (ORDER BY "demand-coverage" desc, investment asc) = 1 THEN 'best'
  WHEN rank() OVER (PARTITION BY investment ORDER BY "demand-coverage" desc) = 1 THEN 'optimal'
  ELSE NULL
END as label
FROM scenarios
WHERE "project-id" = :project-id
ORDER BY
CASE
  WHEN name = 'Initial' THEN 1
  WHEN rank() OVER (ORDER BY "demand-coverage" desc, investment asc) = 1 THEN 2
  WHEN rank() OVER (PARTITION BY investment ORDER BY "demand-coverage" desc) = 1 THEN 3
  ELSE 4
END ASC, "demand-coverage" DESC

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
