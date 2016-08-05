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
SELECT
  projects.id, projects.goal, projects.region_id AS "region-id", projects.stats,
  regions.name AS "region-name", owner_id AS "owner-id"
FROM projects
INNER JOIN regions ON projects.region_id = regions.id
WHERE projects.owner_id = :user-id
ORDER BY projects.id ASC;

-- :name select-project :? :1
SELECT id, goal, region_id AS "region-id", stats, filters, owner_id AS "owner-id"
FROM projects
WHERE id = :id;

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
