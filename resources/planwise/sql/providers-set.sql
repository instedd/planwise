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

-- :name db-list-providers-set :?
SELECT id,
       name,
       "last-version",
       (SELECT COUNT(*) FROM "sites2" WHERE sites2."provider-set-id" = providers_set.id) AS "site-count"
    FROM providers_set
    WHERE "owner-id" = :owner-id;

-- :name db-create-site! :<! :1
INSERT INTO sites2
    ("source-id", type, version, "provider-set-id", name, lat, lon, the_geom, capacity, tags)
    VALUES (:source-id, :type, :version, :provider-set-id, :name, :lat, :lon,
            ST_SetSRID(ST_MakePoint(:lon, :lat), 4326), :capacity, :tags)
    RETURNING id;

-- :name db-find-sites :?
SELECT * FROM sites2
    WHERE "provider-set-id" = :provider-set-id
    AND version = :version;

-- :name db-find-sites-with-coverage-in-region :?
SELECT s2.id, s2.name, s2.lat, s2.lon, s2.capacity, s2.type, s2.tags, s2c.raster
    FROM sites2 s2
    INNER JOIN sites2_coverage s2c
          ON s2.id = s2c."site-id"
    WHERE "provider-set-id" = :provider-set-id
          AND version = :version
          AND s2."the_geom" @ (SELECT "the_geom" FROM regions WHERE id = :region-id)
          AND s2c.algorithm = :algorithm
          AND s2c.options = :options
         /*~ (if (seq (:tags params)) */
          AND s2.tags::tsvector @@ :tags::tsquery;
         /*~ ) ~*/;

-- :name db-count-sites-with-tags :? :1
SELECT COUNT(*) FROM sites2 s2
    WHERE "provider-set-id" = :provider-set-id
    AND version = :version
    AND s2."the_geom" @ (SELECT "the_geom" FROM regions WHERE id = :region-id)
    /*~ (if (seq (:tags params)) */
    AND s2.tags::tsvector @@ :tags::tsquery;
    /*~ ) ~*/;

-- :name db-enum-site-ids :?
SELECT "id" FROM "sites2"
       WHERE "provider-set-id" = :provider-set-id
       AND "version" = :version;

-- :name db-fetch-site-by-id :? :1
SELECT "id", "source-id", "provider-set-id", "version", "name", "lat", "lon", "processing-status"
       FROM "sites2"
       WHERE "id" = :id;

-- :name db-update-site-processing-status! :! :n
UPDATE "sites2"
       SET "processing-status" = :processing-status
       WHERE "id" = :id;

-- :name db-delete-algorithm-coverages-by-site-id! :! :n
DELETE FROM "sites2_coverage"
       WHERE "site-id" = :site-id
       AND "algorithm" = :algorithm;

-- :name db-create-site-coverage! :<! :1
INSERT INTO "sites2_coverage"
       ("site-id", "algorithm", "options", "geom", "raster")
       VALUES (:site-id, :algorithm, :options, :geom, :raster)
       RETURNING "id";
