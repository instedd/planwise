CREATE TABLE IF NOT EXISTS populations (
  id BIGSERIAL PRIMARY KEY,
  source_id BIGINT NOT NULL REFERENCES population_sources(id),
  region_id BIGINT NOT NULL REFERENCES regions(id),
  total_population INT NULL,
  max_population INT NULL,
  raster_pixel_area INT NULL
);
