CREATE EXTENSION IF NOT EXISTS postgis;
CREATE EXTENSION IF NOT EXISTS pgrouting;

-- Populate the ways_nodes table with all the vertices in each way
-- This *will* produce duplicate nodes as it is.
CREATE OR REPLACE FUNCTION populate_ways_nodes()
RETURNS void AS $$
BEGIN
  TRUNCATE TABLE ways_nodes;
  INSERT INTO ways_nodes (gid, lon, lat)
    SELECT
      gid,
      ST_x(ST_PointN(the_geom, g.generate_series)) AS lon,
      ST_y(ST_PointN(the_geom, g.generate_series)) AS lat
    FROM
      ways,
      (SELECT generate_series(1, (SELECT MAX(ST_NPoints(the_geom)) FROM ways))) g
    WHERE g.generate_series <= ST_NPoints(the_geom);
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION apply_traffic_factor(factor float)
RETURNS void AS $$
BEGIN
UPDATE ways
SET cost_s = length_m / (maxspeed_forward * 5 / 18) * factor;
END;
$$ LANGUAGE plpgsql;

create or replace function calculate_aligned_extent(facility_polygon_id integer, region_id integer, srs_resolution double precision, block_size integer)
returns text as $$
declare
  isochrone_xmin double precision;
  isochrone_ymin double precision;
  isochrone_xmax double precision;
  isochrone_ymax double precision;
  region_xmin double precision;
  region_ymin double precision;
  region_xmax double precision;
  region_ymax double precision;
  unaligned_target_minx double precision;
  unaligned_target_miny double precision;
  unaligned_target_maxx double precision;
  unaligned_target_maxy double precision;
  srs_block_length double precision;
  minx_blocks double precision;
  target_minx double precision;
  maxx_blocks double precision;
  target_maxx double precision;
  miny_blocks double precision;
  target_miny double precision;
  maxy_blocks double precision;
  target_maxy double precision;
  aux double precision;
begin
  select into isochrone_xmin, isochrone_ymin, isochrone_xmax, isochrone_ymax
  st_xmin(the_geom), st_ymin(the_geom), st_xmax(the_geom), st_ymax(the_geom)
  from facilities_polygons
  where id = facility_polygon_id;

  select into region_xmin, region_ymin, region_xmax, region_ymax
  st_xmin(the_geom), st_ymin(the_geom), st_xmax(the_geom), st_ymax(the_geom)
  from regions
  where id = region_id;

  unaligned_target_minx := greatest(isochrone_xmin, region_xmin);
  unaligned_target_miny := greatest(isochrone_ymin, region_ymin);
  unaligned_target_maxx := least(isochrone_xmax, region_xmax);
  unaligned_target_maxy := least(isochrone_ymax, region_ymax);

  srs_block_length := srs_resolution*block_size;

  minx_blocks := trunc((unaligned_target_minx-region_xmin)/srs_block_length);
  target_minx := region_xmin+minx_blocks*srs_block_length;
  maxx_blocks := trunc((region_xmax-unaligned_target_maxx)/srs_block_length);
  target_maxx := region_xmax-maxx_blocks*srs_block_length;

  miny_blocks := trunc((unaligned_target_miny-region_ymin)/srs_block_length);
  target_miny := region_ymin+miny_blocks*srs_block_length;
  maxy_blocks := trunc((region_ymax-unaligned_target_maxy)/srs_block_length);
  target_maxy := region_ymax+maxy_blocks*srs_block_length;

  return target_minx || '|' || target_miny || '|' || target_maxx || '|' || target_maxy;
end;
$$ language plpgsql;
