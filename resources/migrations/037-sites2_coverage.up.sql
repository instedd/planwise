ALTER TABLE "sites2" RENAME COLUMN "id" TO "source-id";
ALTER TABLE "sites2" ADD COLUMN "id" SERIAL PRIMARY KEY;
ALTER TABLE "sites2" ADD COLUMN "processing-status" VARCHAR;

CREATE TABLE "sites2_coverage" (
       "id" SERIAL PRIMARY KEY,
       "site-id" INTEGER REFERENCES sites2(id),
       "algorithm" VARCHAR(100) NOT NULL,
       "options" VARCHAR NOT NULL,
       "geom" GEOMETRY(Polygon,4326),
       "raster" VARCHAR
);

