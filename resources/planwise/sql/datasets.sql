-- :name select-datasets-for-user :?
SELECT
  id,
  name,
  description,
  facility_count AS "facility-count",
  owner_id AS "owner-id"
FROM datasets
WHERE owner_id = :user-id
ORDER BY id ASC;
