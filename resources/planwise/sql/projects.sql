-- :name insert-project! :<!
INSERT INTO projects (goal)
    VALUES (:goal)
    RETURNING id;

-- :name select-projects :?
SELECT id, goal
FROM projects
ORDER BY id ASC;
