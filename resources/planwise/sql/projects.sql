-- :name insert-project! :<! :1
INSERT INTO projects (goal, region_id, stats)
    VALUES (:goal, :region-id, :stats)
    RETURNING id;

-- :name select-projects :?
SELECT
  projects.id, projects.goal, projects.region_id, projects.stats,
  regions.name AS region_name
FROM projects
INNER JOIN regions ON projects.region_id = regions.id
ORDER BY projects.id ASC;

-- :name select-project :? :1
SELECT id, goal, region_id, stats, filters
FROM projects
WHERE id = :id;

-- :name update-project* :! :n
UPDATE projects
SET stats = :stats
WHERE projects.id = :project-id
