-- :name insert-project! :<! :1
INSERT INTO projects (goal, dataset_id, region_id, stats, owner_id)
    VALUES (:goal, :dataset-id, :region-id, :stats, :owner-id)
    RETURNING id;

-- :name select-projects-for-dataset :?
SELECT
  projects.id, projects.goal, projects.region_id AS "region-id", projects.stats,
  regions.name AS "region-name", owner_id AS "owner-id"
FROM projects
INNER JOIN regions ON projects.region_id = regions.id
WHERE projects.dataset_id = :dataset-id
ORDER BY projects.id ASC;

-- :name select-projects-for-user :?
-- This query should include all fields necessary for the project list
SELECT
  projects.id,
  projects.goal,
  projects.region_id AS "region-id",
  projects.stats,
  regions.name AS "region-name",
  regions.total_population AS "region-population",
  regions.max_population AS "region-max-population",
  projects.owner_id AS "owner-id",
  owner.email AS "owner-email",
  projects.share_token AS "share-token"
FROM projects
INNER JOIN regions ON projects.region_id = regions.id
INNER JOIN users AS owner ON projects.owner_id = owner.id
LEFT JOIN project_shares AS ps ON ps.project_id = projects.id AND ps.user_id = :user-id
WHERE projects.owner_id = :user-id OR ps.user_id = :user-id
ORDER BY projects.id ASC;

-- :name select-project :? :1
-- This query includes all the fields for the project view, as well as those for
-- the project list
SELECT
  projects.id,
  projects.goal,
  projects.dataset_id AS "dataset-id",
  projects.region_id AS "region-id",
  projects.stats,
  projects.filters,
  regions.name AS "region-name",
  regions.total_population AS "region-population",
  regions.max_population AS "region-max-population",
  ST_Area(regions.the_geom::geography) / 1000000 as "region-area-km2",
  projects.owner_id AS "owner-id",
  owner.email AS "owner-email",
  projects.share_token AS "share-token"
FROM projects
INNER JOIN regions ON projects.region_id = regions.id
INNER JOIN users AS owner ON projects.owner_id = owner.id
WHERE projects.id = :id
/*~ (if (:user-id params) */
AND (projects.owner_id = :user-id
     OR EXISTS (SELECT user_id
                FROM project_shares AS ps
                WHERE ps.user_id = :user-id
                  AND ps.project_id = :id))
/*~ ) ~*/;

-- :name update-project* :! :n
/* :require [clojure.string :as string] */
UPDATE projects SET
/*~
(string/join ","
  (for [field [:goal :stats :filters] :when (some? (field params))]
    (str (name field) " = :" (name field))))
~*/
WHERE projects.id = :project-id;

-- :name delete-project* :! :n
DELETE FROM projects
WHERE projects.id = :id;

-- :name list-project-shares* :?
SELECT users.email AS "user-email", ps.user_id AS "user-id", ps.project_id AS "project-id"
FROM project_shares AS ps
INNER JOIN users ON users.id = ps.user_id
WHERE ps.project_id = :project-id;

-- :name create-project-share! :! :n
-- Note we are using postgresql 9.4, which does not yet support ON CONFLICT DO NOTHING
INSERT INTO project_shares (user_id, project_id)
SELECT :user-id, :project-id
WHERE NOT EXISTS (SELECT 1 FROM project_shares
                  WHERE project_id = :project-id
                    AND user_id = :user-id);

-- :name delete-project-share* :! :n
DELETE FROM project_shares
WHERE project_shares.project_id = :project-id
  AND project_shares.user_id = :user-id;

-- :name reset-share-token* :<! :1
UPDATE projects
SET share_token = gen_random_uuid()
WHERE id = :id
RETURNING share_token AS "share-token";
