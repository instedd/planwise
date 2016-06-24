-- :name create-facilities-table :!
-- :result :raw
-- :doc Create the facilities table
CREATE TABLE facilities (
        id BIGINT PRIMARY KEY,
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
    VALUES (:id, :name, :lat, :lon, ST_SetSRID(ST_MakePoint(:lon, :lat), 4326));

-- :name delete-facilities! :!
DELETE FROM facilities;

-- :name select-facilities :?
SELECT
    id, name, lat, lon
FROM facilities
WHERE name LIKE '%HOSPITAL%'
ORDER BY name;

-- :name facilities-with-isochrones :?
SELECT
  fp.facility_id, f.name, f.lat, f.lon, ST_AsGeoJSON(ST_Simplify(fp.the_geom, :simplify)) AS isochrone
FROM facilities_polygons fp
INNER JOIN facilities f
ON fp.facility_id = f.id
WHERE fp.threshold = :threshold
AND fp.method = :method;

-- :name isochrone-for-facilities :? :1
SELECT
  ST_AsGeoJSON(ST_Union(the_geom))
FROM facilities_polygons
WHERE threshold = :threshold
AND fp.method = 'alpha-shape';
