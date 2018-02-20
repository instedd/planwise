-- :name select-analyses :?
SELECT id, name FROM analyses WHERE owner_id = :owner-id;

-- :name select-analysis :? :1
SELECT id, name FROM analyses WHERE id = :id;

-- :name create-analysis! :<! :1
INSERT INTO analyses (name, owner_id)
       VALUES (:name, :owner-id)
       RETURNING id;
