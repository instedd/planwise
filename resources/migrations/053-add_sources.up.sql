CREATE TABLE IF NOT EXISTS sources (
  id BIGSERIAL PRIMARY KEY,
  set_id BIGINT NOT NULL REFERENCES sources_set(id),
  name VARCHAR(255) NOT NULL,
  type TEXT NOT NULL,
  the_geom geometry(POINT, 4326) NOT NULL,
  quantity INT NULL
);
