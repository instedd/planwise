-- :name db-create-project! :<! :1
INSERT INTO projects2
  ("owner-id", name)
  VALUES (:owner-id, :name)
  RETURNING id;

-- :name db-update-project :!
UPDATE projects2
  SET name = :name
  WHERE id = :id;

-- :name db-get-project :?
SELECT * FROM projects2
    WHERE id = :id;
