-- :name db-create-dataset! :<! :1
INSERT INTO datasets2
    (name, "last-version", "owner-id")
    VALUES (:name, 0, :owner-id)
    RETURNING id;

-- :name db-create-dataset-version! :<! :1
UPDATE datasets2
    SET "last-version" = "last-version" + 1
    WHERE id = :id
    RETURNING "last-version";

-- :name db-find-dataset :?
SELECT * FROM datasets2
  WHERE id = :id

-- :name db-list-datasets :?
SELECT id, name, "last-version" FROM datasets2
    WHERE "owner-id" = :owner-id;

-- :name db-create-site! :<! :1
INSERT INTO sites2
    (id, type, version, "dataset-id", name, lat, lon, the_geom, capacity, tags)
    VALUES (:id, :type, :version, :dataset-id, :name, :lat, :lon,
            ST_SetSRID(ST_MakePoint(:lon, :lat), 4326), :capacity, :tags)
    RETURNING id;

-- :name db-find-sites :?
SELECT * FROM sites2
    WHERE "dataset-id" = :dataset-id
    AND version = :version;
