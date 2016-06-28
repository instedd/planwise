CREATE TABLE facilities (
        id BIGINT PRIMARY KEY,
        name VARCHAR(255) NOT NULL,
        lat NUMERIC(11,8) NOT NULL,
        lon NUMERIC(11,8) NOT NULL,
        the_geom GEOMETRY(Point, 4326) NOT NULL);

CREATE INDEX facilities_the_geom_idx ON facilities USING gist (the_geom);
