-- :name db-create-provider-set! :<! :1
INSERT INTO providers_set
    (name, "last-version", "owner-id")
    VALUES (:name, 0, :owner-id)
    RETURNING id;

-- :name db-create-provider-set-version! :<! :1
UPDATE providers_set
    SET "last-version" = "last-version" + 1
    WHERE id = :id
    RETURNING "last-version";

-- :name db-find-provider-set :? :1
SELECT * FROM providers_set
  WHERE id = :id

-- :name db-find-provider :? :1
SELECT p.type, p.name, p.lat, p.lon, p.capacity, p.tags
    FROM providers p
    WHERE p.id = :id;

-- :name db-list-providers-set :?
SELECT id,
       name,
       "last-version",
       (SELECT COUNT(*) FROM "providers" WHERE providers."provider-set-id" = "providers_set".id) AS "provider-count",
       (SELECT COUNT(*) FROM "projects2" WHERE projects2."provider-set-id" = "providers_set".id) AS "depending-projects"
    FROM providers_set
    WHERE "owner-id" = :owner-id;

-- :name db-create-provider! :<! :1
INSERT INTO providers
    ("source-id", type, version, "provider-set-id", name, lat, lon, the_geom, capacity, tags)
    VALUES (:source-id, :type, :version, :provider-set-id, :name, :lat, :lon,
            ST_SetSRID(ST_MakePoint(:lon, :lat), 4326), :capacity, :tags)
    RETURNING id;

-- :name db-find-providers :?
SELECT * FROM providers
    WHERE "provider-set-id" = :provider-set-id
    AND version = :version;

-- :name db-find-providers-in-region :?
SELECT p.id, p.name, p.lat, p.lon, p.capacity, p.type, p.tags
    FROM providers p
    WHERE "provider-set-id" = :provider-set-id
          AND version = :version
          AND p."the_geom" @ (SELECT "the_geom" FROM regions WHERE id = :region-id)
         /*~ (if (seq (:tags params)) */
          AND p.tags::tsvector @@ :tags::tsquery;
         /*~ ) ~*/;

-- :name db-count-providers-with-tags :? :1
SELECT COUNT(*) FROM providers p
    WHERE "provider-set-id" = :provider-set-id
    AND version = :version
    AND p."the_geom" @ (SELECT "the_geom" FROM regions WHERE id = :region-id)
    /*~ (if (seq (:tags params)) */
    AND p.tags::tsvector @@ :tags::tsquery;
    /*~ ) ~*/;

-- :name db-enum-provider-ids :?
SELECT "id" FROM "providers"
       WHERE "provider-set-id" = :provider-set-id
       AND "version" = :version;

-- :name db-fetch-provider-by-id :? :1
SELECT "id", "source-id", "provider-set-id", "version", "name", "lat", "lon", "processing-status"
       FROM "providers"
       WHERE "id" = :id;

-- :name db-delete-providers! :!
DELETE FROM "providers"
    WHERE "provider-set-id" = :provider-set-id;

-- :name db-delete-provider-set! :!
DELETE FROM "providers_set"
    WHERE id = :provider-set-id;
