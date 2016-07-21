ALTER TABLE facilities_polygons ADD COLUMN starting_node INTEGER;


-- generates the isochrone polygons for all thresholds for a single facility
CREATE OR REPLACE FUNCTION process_facility_isochrones(f_id bigint, _method varchar, threshold_start integer, threshold_finish integer, threshold_jump integer)
returns void as $$
declare
  from_cost integer;
  to_cost integer;
  facility_node integer;
begin
  create temporary table if not exists edges_agg_cost (
    gid integer not null,
    agg_cost double precision
  );

  facility_node := (SELECT closest_node(the_geom) FROM facilities WHERE id = f_id);
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
        ) as buffers
      );
    ELSIF _method = 'alpha-shape' THEN
      BEGIN
        insert into facilities_polygons (
               select f_id, to_cost, _method, st_buffer(st_setsrid(pgr_pointsaspolygon('select id::integer, lon::float as x, lat::float as y from ways_nodes where gid in (select gid from edges_agg_cost where agg_cost < ' || to_cost || ')'),4326), 0.004));
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
