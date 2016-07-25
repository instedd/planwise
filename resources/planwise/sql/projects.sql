-- :name insert-project! :<! :1
INSERT INTO projects (goal, region_id, facilities_count)
    VALUES (:goal, :region-id, :facilities-count)
    RETURNING id;

-- :name select-projects :?
SELECT id, goal, region_id, facilities_count
FROM projects
ORDER BY id ASC;

-- :name select-project :? :1
SELECT id, goal, region_id, facilities_count
FROM projects
WHERE id = :id;

-- :name update-project* :! :n
UPDATE projects
SET facilities_count = :facilities-count
WHERE projects.id = :project-id
