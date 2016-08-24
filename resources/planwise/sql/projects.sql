-- :name insert-project! :<! :1
INSERT INTO projects (goal, region_id, stats, owner_id)
    VALUES (:goal, :region-id, :stats, :owner-id)
    RETURNING id;

-- :name select-projects :?
SELECT
  projects.id, projects.goal, projects.region_id AS "region-id", projects.stats,
  regions.name AS "region-name", owner_id AS "owner-id"
FROM projects
INNER JOIN regions ON projects.region_id = regions.id
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
  owner_id AS "owner-id"
FROM projects
INNER JOIN regions ON projects.region_id = regions.id
WHERE projects.owner_id = :user-id
ORDER BY projects.id ASC;

-- :name select-project :? :1
-- This query includes all the fields for the project view, as well as those for
-- the project list
SELECT
  projects.id,
  projects.goal,
  projects.region_id AS "region-id",
  projects.stats,
  projects.filters,
  regions.name AS "region-name",
  regions.total_population AS "region-population",
  regions.max_population AS "region-max-population",
  ST_Area(regions.the_geom::geography) / 1000000 as "region-area-km2",
  projects.owner_id AS "owner-id"
FROM projects
INNER JOIN regions ON projects.region_id = regions.id
WHERE projects.id = :id;

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
