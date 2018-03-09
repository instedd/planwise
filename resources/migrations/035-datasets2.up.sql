CREATE TABLE IF NOT EXISTS datasets2 (
       id BIGSERIAL PRIMARY KEY,
       name VARCHAR(255) NOT NULL,
       "last-version" INT,
       "owner-id" BIGINT NOT NULL REFERENCES users(id)
);

CREATE TABLE IF NOT EXISTS sites2 (
      id INT NOT NULL,
      version INT,
      "dataset-id" BIGINT NOT NULL REFERENCES datasets2(id),
      name VARCHAR(255) NOT NULL,
      lat NUMERIC(11,8) NOT NULL,
      lon NUMERIC(11,8) NOT NULL,
      the_geom GEOMETRY(Point, 4326) NOT NULL,
      capacity INT,
      type TEXT,
      tags TEXT
);
