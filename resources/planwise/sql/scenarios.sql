-- :name db-list-scenarios :?
SELECT id, name, investment, "demand-coverage", label
FROM scenarios
WHERE "project-id" = :project-id
ORDER BY
CASE
  WHEN label = 'initial' THEN 1
  WHEN label = 'best'    THEN 2
  WHEN label = 'optimal' THEN 3
  ELSE 4
END ASC, "demand-coverage" DESC

-- :name db-update-scenarios-label! :!
UPDATE scenarios
SET label = computed.label
FROM (
  SELECT id,
    CASE
    WHEN rank() OVER (ORDER BY "demand-coverage" desc, investment asc) = 1 THEN 'best'
    WHEN rank() OVER (PARTITION BY investment ORDER BY "demand-coverage" desc) = 1 THEN 'optimal'
    ELSE NULL
    END as label
  FROM scenarios
  WHERE "project-id" = :project-id
    AND (label IS NULL OR label <> 'initial')
    AND "demand-coverage" IS NOT NULL
) AS computed
WHERE scenarios.id = computed.id

-- :name db-find-scenario :? :1
SELECT *
FROM scenarios
WHERE id = :id

-- :name db-create-scenario! :<! :1
INSERT INTO scenarios
  (name, "project-id", investment, "demand-coverage", changeset, label)
VALUES
  (:name, :project-id, :investment, NULL, :changeset, :label)
RETURNING id;

-- :name db-update-scenario! :! :1
UPDATE scenarios
  SET name = :name, investment = :investment,
  "demand-coverage" = :demand-coverage, changeset = :changeset
WHERE
  id = :id

-- :name db-list-scenarios-names :?
SELECT name
FROM scenarios
WHERE "project-id" = :project-id
  AND name ILIKE (:name || '%')
