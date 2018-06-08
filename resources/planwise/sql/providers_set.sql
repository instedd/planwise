-- :name db-create-provider-set! :<! :1
INSERT INTO providers_set
    (name, "last-version", "owner-id", "coverage-algorithm")
    VALUES (:name, 0, :owner-id, :coverage-algorithm)
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
       (SELECT COUNT(*) FROM "providers" WHERE providers."provider-set-id" = "providers_set".id) AS "provider-count"
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

-- :name db-find-providers-with-coverage-in-region :?
SELECT p.id, p.name, p.lat, p.lon, p.capacity, p.type, p.tags, pc.raster
    FROM providers p
    INNER JOIN providers_coverage pc
          ON p.id = pc."provider-id"
    WHERE "provider-set-id" = :provider-set-id
          AND version = :version
          AND p."the_geom" @ (SELECT "the_geom" FROM regions WHERE id = :region-id)
          AND pc.algorithm = :algorithm
          AND pc.options = :options
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

-- :name db-update-provider-processing-status! :! :n
UPDATE "providers"
       SET "processing-status" = :processing-status
       WHERE "id" = :id;

-- :name db-delete-algorithm-coverages-by-provider-id! :! :n
DELETE FROM "providers_coverage"
       WHERE "provider-id" = :provider-id
       AND "algorithm" = :algorithm;

-- :name db-create-provider-coverage! :<! :1
INSERT INTO "providers_coverage"
       ("provider-id", "algorithm", "options", "geom", "raster")
       VALUES (:provider-id, :algorithm, :options, :geom, :raster)
       RETURNING "id";
