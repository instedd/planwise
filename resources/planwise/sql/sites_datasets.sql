-- :name create-dataset! :<! :1
INSERT INTO sites_datasets
    (name, "last-version", "owner-id")
    VALUES (:name, 0, :owner-id)
    RETURNING id;

-- :name create-dataset-version! :<! :1
UPDATE sites_datasets
    SET "last-version" = "last-version" + 1
    WHERE id = :id
    RETURNING "last-version";

-- :name find-dataset :?
SELECT * FROM sites_datasets
  WHERE id = :id

-- :name list-datasets :?
SELECT id, name, "last-version" FROM sites_datasets
    WHERE "owner-id" = :owner-id;

-- :name create-site! :<! :1
INSERT INTO sites_facilities
    (id, type, version, "dataset-id", name, lat, lon, the_geom, capacity, tags)
    VALUES (:id, :type, :version, :dataset-id, :name, :lat, :lon,
            ST_SetSRID(ST_MakePoint(:lon, :lat), 4326), :capacity, :tags)
    RETURNING id;

-- :name find-sites :?
SELECT * FROM sites_facilities
    WHERE "dataset-id" = :dataset-id
    AND version = :version;
