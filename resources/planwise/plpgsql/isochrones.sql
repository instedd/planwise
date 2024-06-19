CREATE EXTENSION IF NOT EXISTS postgis;
CREATE EXTENSION IF NOT EXISTS pgrouting;

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
    perform process_facility_isochrones(f_row.id, method, threshold_start, threshold_finish, threshold_jump);
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
    select gid, ST_Buffer(the_geom::geography, buffer_radius_in_meters)::geometry
    from ways
  );
end;
$$ language plpgsql;

-- generates the isochrone polygons for all thresholds for a single facility
DROP FUNCTION IF EXISTS process_facility_isochrones(bigint, varchar, integer, integer, integer);
CREATE OR REPLACE FUNCTION process_facility_isochrones(f_id bigint, _method varchar, threshold_start integer, threshold_finish integer, threshold_jump integer)
returns RECORD as $$
declare
  from_cost integer;
  to_cost integer;
  facility_node integer;
  closest_node_geom geometry(point, 4326);
  facility_geom geometry(point, 4326);
  buffer_length integer;
  polygon_id integer;
  country text;
  bounding_radius_meters float;
  distance_threshold_meters float;
  ret record; -- (exit_code, facility_country)
begin
  create temporary table if not exists edges_agg_cost (
    gid integer not null,
    agg_cost double precision,
    node integer not null
  );

  facility_node := (SELECT closest_node(the_geom) FROM facilities WHERE id = f_id);
  facility_geom := (select the_geom from facilities where id = f_id);
  closest_node_geom := (select the_geom from ways_vertices_pgr where id = facility_node);

  country := (SELECT r.country FROM REGIONS AS r WHERE ST_Contains(r.the_geom, facility_geom) AND r.admin_level = 2 LIMIT 1);

  IF country IS NULL THEN
    UPDATE facilities SET processing_status = 'outside-regions' WHERE id = f_id;
    RAISE NOTICE 'warning: Facility % not processed, it is outside the regions boundaries.', f_id;

    SELECT 'outside-regions'::TEXT, NULL::TEXT into ret; RETURN ret;
  END IF;

  -- Maximum distance from the closest node in the road network to discard a facility
  -- This number was adjusted manually such that the ~90% of current facility dataset from Kenya is not discarded
  distance_threshold_meters := 5000;

  IF ST_Distance(ST_GeogFromWKB(facility_geom), ST_GeogFromWKB(closest_node_geom)) > distance_threshold_meters THEN
    UPDATE facilities SET processing_status = 'no-road-network' WHERE id = f_id;
    RAISE NOTICE 'warning: Facility % not processed, it is too far from the road network.', f_id;
    SELECT 'no-road-network'::TEXT, NULL::TEXT into ret; RETURN ret;
  END IF;

  -- Apply an upper bound to the reachable region to avoid retrieving all ways
  -- bound = area that can be covered travelling in a straight line at 85 km/h
  -- for the maximum threshold time.
  bounding_radius_meters := (threshold_finish / 60.0) * 85 * 1000;

  IF facility_node IS NOT NULL THEN
    insert into edges_agg_cost (
      select e.edge, e.agg_cost, e.node
      from pgr_drivingdistance(
        'select gid as id, source, target, cost_s as cost from ways where the_geom @ (select ST_Buffer(the_geom::geography, '|| bounding_radius_meters || ')::geometry from facilities where id = ' || f_id || ')',
        facility_node,
        threshold_finish * 60,
        false) e
    );
  END IF;

  -- This snippet adds edges that may be left out but should be included.
  -- Since pgr_drivingDistance uses the Dijkstra's algorithm and its corresponding
  -- spanning tree, if there are edges between two reachable nodes that do not
  -- belong to the shortest path to either of them then that edge won't be included
  -- but it should if traversing it doesn't exceeds the maximum threshold.
  insert into edges_agg_cost (
    select w.gid, least(e_source.agg_cost, e_target.agg_cost) + w.cost as agg_cost, w.target
    from ways w
    join edges_agg_cost e_source on e_source.node = w.source
    join edges_agg_cost e_target on e_target.node = w.target
    where w.cost + least(e_source.agg_cost, e_target.agg_cost) < threshold_finish * 60
  );

  from_cost := 0;
  to_cost   := threshold_start * 60;

  -- Buffer in meters to apply around the alpha shape polygon
  buffer_length := 300;

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
          select ST_Buffer(ST_GeogFromWKB(ST_MakeLine(facility_geom, closest_node_geom)), buffer_length)::geometry
        ) as buffers
      ) returning id INTO polygon_id;
    ELSIF _method = 'alpha-shape' THEN
      BEGIN
        insert into facilities_polygons (
        select f_id, to_cost, _method, st_buffer(st_geogfromwkb(st_setsrid(patched_pointsAsPolygon('(select id::integer, lon::float as x, lat::float as y from ways_nodes where gid in (select gid from edges_agg_cost where agg_cost < ' || to_cost || ')) union (select id::integer, lon::float as x, lat::float as y from facilities f where f.id = ' || f_id || ')'), 4326)), buffer_length)::geometry)
        returning id INTO polygon_id;
      EXCEPTION WHEN OTHERS THEN
        RAISE NOTICE 'Failed to calculate alpha shape for facility %', f_id;
      END;
    ELSE
      RAISE EXCEPTION 'Method % unknown. Please use buffer or alpha-shape', _method;
      SELECT 'error'::TEXT, NULL::TEXT into ret; RETURN ret;
    END IF;

    -- Precalculate area
    UPDATE facilities_polygons SET area = ST_Area(the_geom) WHERE id = polygon_id;

    -- Populate facilities_polygons_regions with the intersection between the inserted polygon and regions
    INSERT INTO facilities_polygons_regions(facility_polygon_id, region_id, area)
    SELECT fp.id, r.id, ST_Area(ST_Intersection(fp.the_geom, r.the_geom))
    FROM facilities AS f
      INNER JOIN facilities_polygons AS fp ON f.id = fp.facility_id
      INNER JOIN regions AS r ON f.the_geom @ r.the_geom AND ST_Contains(r.the_geom, f.the_geom)
    WHERE fp.id = polygon_id
      AND r.admin_level > 2;

    from_cost      := to_cost;
    to_cost        := to_cost + threshold_jump * 60;
  end loop;

  UPDATE facilities SET processing_status = 'ok' WHERE id = f_id;

  delete from edges_agg_cost;

  -- Cannot drop the temp table when running the alpha shape algorithm because
  -- pgr holds a reference to it
  -- drop table edges_agg_cost;

  SELECT 'ok'::TEXT, country::TEXT into ret; RETURN ret;
end;
$$ language plpgsql;
