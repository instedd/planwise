-- :name select-user :? :1
SELECT id, email, full_name, last_login
FROM users
WHERE id = :id;

-- :name select-user-by-email :? :1
SELECT id, email, full_name, last_login
FROM users
WHERE email = :email;

-- :name create-user! :<!
INSERT INTO users (email, full_name, created_at)
       VALUES(:email, :full_name, 'now')
       RETURNING id;

-- :name update-last-login! :! :n
UPDATE users
SET last_login = 'now'
WHERE id = :id;
