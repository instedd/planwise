-- :name db-list-sources :?
SELECT id,
       name,
       type,
       (SELECT COUNT(*) FROM sources WHERE sources.set_id = source_set.id) AS "sources-count"
  FROM source_set
  WHERE "owner-id" = :owner-id OR "owner-id" IS NULL;

-- :name db-create-source-set! :<! :1
INSERT INTO source_set
    (name, type, unit, raster_file, "owner-id")
    VALUES (:name, 'points', :unit, NULL, :owner-id)
    RETURNING id;

-- :name db-find-source-set :? :1
SELECT id,
       name,
       type,
       (SELECT COUNT(*) FROM sources WHERE sources.set_id = source_set.id) AS "sources-count"
  FROM source_set
  WHERE id = :id AND "owner-id" = :owner-id;

-- :name db-find-source-set-by-id :? :1
SELECT id,
       name,
       type,
       (SELECT COUNT(*) FROM sources WHERE sources.set_id = source_set.id) AS "sources-count"
  FROM source_set
  WHERE id = :id;

-- :name db-create-source! :<! :1
INSERT INTO sources
    ("set_id", name, type, "the_geom", "quantity")
    VALUES (:set-id, :name, :type, ST_SetSRID(ST_MakePoint(:lon, :lat), 4326), :quantity)
    RETURNING id;

-- :name db-list-sources-under-provider-coverage :?
SELECT s.*
  FROM
    sources as s,
    (SELECT geom
      FROM providers_coverage
      LEFT JOIN providers ON providers_coverage."provider-id" = providers.id
      WHERE "provider-id" = :provider-id
        AND algorithm = :algorithm
        AND options = :options) AS pc
  WHERE
    s.set_id = :source-set-id AND
    ST_CONTAINS(pc.geom, s.the_geom);

-- :name db-list-sources-in-set :?
SELECT *
  FROM
    sources
  WHERE
    set_id = :source-set-id;