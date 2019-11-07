-- :name db-create-project! :<! :1
INSERT INTO projects2
  ("owner-id", name, config, "region-id", "provider-set-id", "source-set-id", state)
  VALUES (:owner-id, :name, :config, :region-id, :provider-set-id, :source-set-id, :state)
  RETURNING id;

-- :name db-update-project :!
UPDATE projects2
  SET name = :name, config = :config,
      "provider-set-id" = :provider-set-id,
      "region-id" = :region-id,
      "coverage-algorithm" = :coverage-algorithm,
      "source-set-id" = :source-set-id
  WHERE id = :id;

-- :name db-get-project :? :1
SELECT
  projects2.*,
  ST_AsGeoJSON(ST_Envelope(regions.the_geom)) AS bbox,
  source_set.type AS "source-type"
  FROM projects2
         LEFT JOIN regions ON projects2."region-id" = regions.id
         LEFT JOIN providers_set ON projects2."provider-set-id" = providers_set.id
         LEFT JOIN source_set ON projects2."source-set-id" = source_set.id
 WHERE projects2.id = :id;

-- :name db-list-projects :?
SELECT id, name, "region-id", state FROM projects2
    WHERE "owner-id" = :owner-id
    ORDER BY name;

-- :name db-update-state-project :!
UPDATE projects2
  SET state = :state
  WHERE id = :id;

-- :name db-start-project! :!
UPDATE "projects2" AS p2
  SET "state" = 'started',
      "provider-set-version" = (SELECT "last-version" FROM providers_set ps WHERE ps."id" = p2."provider-set-id")
  WHERE "id" = :id;

-- :name db-reset-project! :!
UPDATE "projects2" AS p2
  SET "state" = 'draft',
      "engine-config" = NULL
  WHERE "id" = :id;

-- :name db-delete-project! :!
DELETE FROM projects2
  WHERE id = :id;
