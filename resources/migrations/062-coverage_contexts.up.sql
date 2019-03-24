CREATE TABLE coverage_contexts (
  id BIGSERIAL PRIMARY KEY,    -- id is internal and used for cross-reference with coverages only
  cid VARCHAR(255) UNIQUE,     -- stringified version of the context id
  region_id BIGINT REFERENCES regions(id) ON DELETE CASCADE,
  options TEXT NOT NULL
);

CREATE TABLE coverages (
  context_id BIGINT REFERENCES coverage_contexts(id) ON DELETE CASCADE,
  lid VARCHAR(255) NOT NULL,   -- stringified version of the location id
  location GEOMETRY(Point, 4326) NOT NULL,
  coverage GEOMETRY(MultiPolygon, 4326) NOT NULL,
  raster_file VARCHAR(255),    -- name of the raster file, which is relative to the context directory

  CONSTRAINT id_with_context UNIQUE (context_id, lid)
);
