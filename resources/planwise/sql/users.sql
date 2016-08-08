-- :name select-user :? :1
SELECT id, email, full_name AS "full-name", last_login AS "last-login"
FROM users
WHERE id = :id;

-- :name select-user-by-email :? :1
SELECT id, email, full_name AS "full-name", last_login AS "last-login"
FROM users
WHERE email = :email;

-- :name create-user! :<!
INSERT INTO users (email, full_name, created_at)
       VALUES (:email, :full-name, 'now')
       RETURNING id;

-- :name update-last-login! :! :n
UPDATE users
SET last_login = 'now'
WHERE id = :id;

-- :name save-user-token! :<!
INSERT INTO tokens (user_id, scope, token, refresh_token, expires)
       VALUES ((SELECT id FROM users WHERE email = :email),
               :scope, :token, :refresh-token, :expires)
       RETURNING id;

-- :name find-latest-user-token :? :1
SELECT
  t.id,
  t.user_id AS "user-id",
  t.scope,
  t.token,
  t.refresh_token AS "refresh-token",
  t.expires
FROM tokens t
INNER JOIN users u ON u.id = t.user_id
WHERE u.email = :email AND t.scope = :scope
ORDER BY t.id DESC
LIMIT 1;
