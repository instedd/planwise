-- find closest node to a point
CREATE OR REPLACE FUNCTION closest_node (original geometry(point, 4326))
returns integer as $$
declare
  closest_node integer;
begin
  select w.id
  from ways_vertices_pgr w
  order by w.the_geom <-> original limit 1
  into closest_node;

  return closest_node;
end;
$$ language plpgsql;

-- generates the isochrone polygons for all the facilities and thresholds
CREATE OR REPLACE FUNCTION calculate_isochrones(method varchar, threshold_start integer, threshold_finish integer, threshold_jump integer)
returns void as $$
declare
  from_cost integer;
  to_cost integer;
  f_row record;
  facility_count integer;
  facility_index integer;
begin

  -- Process all facilities
  facility_count := (select count(*) from facilities);
  facility_index := 1;
  for f_row in select * from facilities f loop
    RAISE NOTICE 'Processing facility % (%/%)', f_row.id, facility_index, facility_count;
    select process_facility_isochrones(f_row.id, method, threshold_start, threshold_finish, threshold_jump);
    facility_index := facility_index + 1;
  end loop;

  -- Cannot drop the temp table when running the alpha shape algorithm because
  -- pgr holds a reference to it
  -- drop table edges_agg_cost;

end;
$$ language plpgsql;

-- cache the buffers of the ways
CREATE OR REPLACE FUNCTION cache_ways_buffers(buffer_radius_in_meters float)
returns void as $$
begin
  create temporary table if not exists ways_buffers (
    ways_gid integer not null,
    the_geom geometry
  );
  truncate table ways_buffers;
  insert into ways_buffers (
    select gid, ST_Buffer(ST_GeogFromWKB(the_geom), buffer_radius_in_meters)::geometry
    from ways
  );
end;
$$ language plpgsql;

-- generates the isochrone polygons for all thresholds for a single facility
CREATE OR REPLACE FUNCTION process_facility_isochrones(f_id bigint, _method varchar, threshold_start integer, threshold_finish integer, threshold_jump integer)
returns void as $$
declare
  from_cost integer;
  to_cost integer;
  facility_node integer;
  closest_node_geom geometry(point, 4326);
  facility_geom geometry(point, 4326);
begin
  create temporary table if not exists edges_agg_cost (
    gid integer not null,
    agg_cost double precision
  );

  facility_node := (SELECT closest_node(the_geom) FROM facilities WHERE id = f_id);
  facility_geom := (select the_geom from facilities where id = f_id);
  closest_node_geom := (select the_geom from ways_vertices_pgr where id = facility_node);
  IF ST_Distance(ST_GeogFromWKB(facility_geom), ST_GeogFromWKB(closest_node_geom)) > 1000 THEN
    RAISE NOTICE 'Facility % wont be processed because its too far from the road network.', f_id;
    RETURN;
  END IF;

  insert into edges_agg_cost (
    select e.edge, e.agg_cost
    from pgr_drivingdistance(
      'select gid as id, source, target, cost_s as cost from ways',
      facility_node,
      threshold_finish * 60,
      false) e
  );

  from_cost := 0;
  to_cost   := threshold_start * 60;
  while to_cost <= threshold_finish * 60 loop

    DELETE FROM facilities_polygons fp
           WHERE fp.facility_id = f_id
           AND fp.method = _method
           AND fp.threshold = to_cost;

    IF _method = 'buffer' THEN
      insert into facilities_polygons (
        select f_id, to_cost, _method, st_union(buffers.the_geom), starting_node
        from (
          select wb.the_geom
          from edges_agg_cost eac
          join ways_buffers wb on wb.ways_gid = eac.gid
          where agg_cost >= from_cost and agg_cost < to_cost
          union
          select the_geom
          from facilities_polygons
          where facility_id = f_id and threshold = (to_cost-threshold_jump * 60)
          union
          select ST_Buffer(ST_GeogFromWKB(ST_MakeLine(facility_geom, closest_node_geom)), 300)::geometry
        ) as buffers
      );
    ELSIF _method = 'alpha-shape' THEN
      BEGIN
        insert into facilities_polygons (
        select f_id, to_cost, _method, st_buffer(st_geogfromwkb(st_setsrid(pgr_pointsaspolygon('(select id::integer, lon::float as x, lat::float as y from ways_nodes where gid in (select gid from edges_agg_cost where agg_cost < ' || to_cost || ')) union (select id::integer, lon::float as x, lat::float as y from facilities f where f.id = ' || f_id || ')'), 4326)), 300)::geometry);
      EXCEPTION WHEN OTHERS THEN
        RAISE NOTICE 'Failed to calculate alpha shape for facility %', f_id;
      END;
    ELSE
      RAISE EXCEPTION 'Method % unknown', _method USING HINT = 'Please use buffer or alpha-shape';
    END IF;

    from_cost      := to_cost;
    to_cost        := to_cost + threshold_jump * 60;
  end loop;

  delete from edges_agg_cost;

  -- Cannot drop the temp table when running the alpha shape algorithm because
  -- pgr holds a reference to it
  -- drop table edges_agg_cost;

end;
$$ language plpgsql;
