create or replace function calculate_aligned_extent(facility_polygon_id integer, region_id integer)
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

  -- calculate extent based on the block size of (128x128) and the precision of 0.0008333
  unaligned_target_minx := greatest(isochrone_xmin, region_xmin);
  unaligned_target_miny := greatest(isochrone_ymin, region_ymin);
  unaligned_target_maxx := least(isochrone_xmax, region_xmax);
  unaligned_target_maxy := least(isochrone_ymax, region_ymax);

  srs_block_length := 0.000833*128;

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

select calculate_aligned_extent(:facility_polygon_id, :region_id);
