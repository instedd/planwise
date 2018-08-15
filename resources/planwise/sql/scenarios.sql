-- :name db-list-scenarios :?
SELECT id, name, investment, "demand-coverage", changeset, label, state
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
SELECT scenarios.*, scenarios."demand-coverage" - initial_scenario."demand-coverage" AS "increase-coverage"
FROM scenarios
LEFT JOIN scenarios AS initial_scenario ON initial_scenario."project-id" = scenarios."project-id" AND initial_scenario.label = 'initial'
WHERE scenarios.id = :id

-- :name db-create-scenario! :<! :1
INSERT INTO scenarios
  (name, "project-id", investment, "demand-coverage", changeset, label, "state", "updated-at")
VALUES
  (:name, :project-id, :investment, NULL, :changeset, :label, 'pending', NOW())
RETURNING id;

-- :name db-update-scenario! :! :1
UPDATE scenarios
  SET name = :name, investment = :investment,
  "demand-coverage" = :demand-coverage, changeset = :changeset, label = :label,
  state = 'pending', "updated-at" = NOW()
WHERE
  id = :id

-- :name db-update-scenario-state! :! :1
UPDATE "scenarios"
  SET "raster" = :raster,
      "demand-coverage" = :demand-coverage,
      "state" = :state,
      "providers-data" = :providers-data,
      "sources-data" = :sources-data,
      "new-providers-geom" = :new-providers-geom
  WHERE "id" = :id;


-- :name db-update-project-engine-config! :! :1
UPDATE "projects2"
  SET "engine-config" = :engine-config
  WHERE "id" = :project-id;

-- :name db-list-scenarios-names :?
SELECT name
FROM scenarios
WHERE "project-id" = :project-id

-- :name db-delete-scenarios! :!
DELETE FROM "scenarios"
  WHERE "project-id" = :project-id;

-- :name db-last-scenario-name :? :1
SELECT upper(name) AS name FROM scenarios
  WHERE name similar to '[A-Za-z]+'
  AND (label <> 'initial' OR label IS NULL)
  AND "project-id" = :project-id
  ORDER BY upper(name) DESC
  LIMIT 1

-- :name db-get-initial-providers-data :? :1
SELECT "providers-data" FROM scenarios
  WHERE "project-id" = :project-id
  AND label = 'initial';

-- :name db-get-initial-sources-data :? :1
SELECT "sources-data" FROM scenarios
  WHERE "project-id" = :project-id
  AND label = 'initial';

-- :name db-get-new-providers-geom :? :1
SELECT "new-providers-geom" FROM scenarios
  WHERE id = :scenario-id;

-- :name db-update-error-message :!
UPDATE scenarios
    SET "error-message" = :msg
    WHERE id = :id;
