CREATE TABLE regions (
  id BIGSERIAL PRIMARY KEY,
  country VARCHAR(255) NOT NULL,
  name VARCHAR(255) NOT NULL,
  admin_level INT,
  the_geom geometry(MultiPolygon,4326)
);

CREATE INDEX regions_country_idx ON regions (country);
