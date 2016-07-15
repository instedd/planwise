-- :name insert-project! :<!
INSERT INTO projects (goal)
    VALUES (:goal)
    RETURNING id;

-- :name select-projects :?
SELECT id, goal, region_id
FROM projects
ORDER BY id ASC;

-- :name select-project :? :1
SELECT id, goal, region_id
FROM projects
WHERE id = :id;
