CREATE TABLE coverage_contexts (
  id VARCHAR(255) PRIMARY KEY,
  options TEXT NOT NULL
);

CREATE TABLE coverages (
  context_id VARCHAR(255) REFERENCES coverage_contexts(id) ON DELETE CASCADE,
  id VARCHAR(255) NOT NULL,
  location GEOMETRY(Point, 4326) NOT NULL,
  coverage GEOMETRY(Polygon, 4326) NOT NULL,
  raster_path VARCHAR(255),

  CONSTRAINT id_with_context UNIQUE (context_id, id)
);
