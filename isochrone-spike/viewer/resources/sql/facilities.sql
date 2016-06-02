-- :name create-facilities-table :!
-- :result :raw
-- :doc Create the facilities table
CREATE TABLE facilities
  (id BIGINT PRIMARY KEY,
   name VARCHAR(255) NOT NULL,
   lat NUMERIC(11,8) NOT NULL,
   lon NUMERIC(11,8) NOT NULL,
   the_geom GEOMETRY(Point, 4326) NOT NULL);

-- :name create-facilities-spatial-index :!
-- :doc Create the spatial index for the facilities table
CREATE INDEX facilities_the_geom_idx ON facilities USING gist (the_geom);

-- :name drop-facilities-table! :!
-- :doc Drop the facilities table if exists
DROP TABLE IF EXISTS facilities;

-- :name insert-facility! :! :n
INSERT INTO facilities
  (id, name, lat, lon, the_geom)
VALUES
  (:id, :name, :lat, :lon, ST_SetSRID(ST_MakePoint(:lon, :lat), 4326));

-- :name delete-facilities! :!
DELETE FROM facilities;

-- :name select-facilities :?
SELECT
  id, name, lat, lon
FROM facilities
WHERE name LIKE '%HOSPITAL%'
ORDER BY name;
