-- :name db-create-project! :<! :1
INSERT INTO projects2
  ("owner-id", name, config, "provider-set-id", state, "deleted-at")
  VALUES (:owner-id, :name, NULL, NULL, :state, NULL)
  RETURNING id;

-- :name db-update-project :!
UPDATE projects2
  SET name = :name, config = :config,
      "provider-set-id" = :provider-set-id,
      "region-id" = :region-id,
      "population-source-id" = :population-source-id
  WHERE id = :id;

-- :name db-get-project :? :1
SELECT projects2.*, providers_set."coverage-algorithm", regions_bbox.bbox
  FROM (
    SELECT projects2.id, ST_AsGeoJSON(ST_Extent(regions."preview_geom")) AS bbox
      FROM regions
        RIGHT JOIN projects2 ON projects2."region-id" = regions.id
      WHERE projects2.id = :id
      GROUP BY projects2.id) AS regions_bbox
  LEFT JOIN projects2 ON projects2.id = regions_bbox.id
  LEFT JOIN providers_set ON projects2."provider-set-id" = providers_set.id
  WHERE projects2.id = :id;

-- :name db-list-projects :?
SELECT id, name, "region-id", state FROM projects2
    WHERE "deleted-at" is NULL
    AND "owner-id" = :owner-id
    ORDER BY name;

-- :name db-update-state-project :!
UPDATE projects2
  SET state = :state
  WHERE id = :id;

-- :name db-start-project! :!
UPDATE "projects2" AS p2
  SET "state" = 'started',
      "provider-set-version" = (SELECT "last-version" FROM providers_set ps WHERE ps."id" = ps."provider-set-id")
  WHERE "id" = :id;

-- :name db-reset-project! :!
UPDATE "projects2" AS p2
  SET "state" = 'draft'
  WHERE "id" = :id;

-- :name db-delete-project! :!
UPDATE projects2
  SET "deleted-at" = NOW()
  WHERE id = :id;
