CREATE OR REPLACE FUNCTION insert_way (class_id integer, speed integer, source bigint, target bigint)
RETURNS integer AS $$
DECLARE
  source_node RECORD;
  target_node RECORD;
  geom GEOMETRY;
  length_m float;
  new_gid BIGINT;
BEGIN
  SELECT * FROM ways_vertices_pgr WHERE id = source INTO source_node;
  SELECT * FROM ways_vertices_pgr WHERE id = target INTO target_node;

  geom := ST_MakeLine(ARRAY[source_node.the_geom, target_node.the_geom]);
  length_m := ST_Length(geom::geography);

  INSERT INTO ways
         (class_id, length, length_m, source, target, x1, y1, x2, y2,
          cost, one_way, maxspeed_forward, the_geom)
         VALUES (class_id, ST_Length(geom), length_m, source, target,
                 ST_X(source_node.the_geom), ST_Y(source_node.the_geom),
                 ST_X(target_node.the_geom), ST_Y(target_node.the_geom),
                 ST_Length(geom), 0, speed, geom)
  RETURNING gid INTO new_gid;
  UPDATE ways_vertices_pgr SET cnt = cnt + 1 WHERE id IN (source, target);

  RETURN new_gid;
END;
$$ LANGUAGE plpgsql;


DROP FUNCTION IF EXISTS connect_isolated_segments (float);
CREATE OR REPLACE FUNCTION connect_isolated_segments (threshold float)
RETURNS integer AS $$
DECLARE
  segment RECORD;
  closest_to_source RECORD;
  closest_to_target RECORD;
  new_way_gid BIGINT;
  isolated INTEGER;
  created_ways INTEGER;
  fixed_segments INTEGER;
  segment_fixed BOOLEAN;
BEGIN
  RAISE NOTICE 'Trying to fix up isolated segments';

  isolated := 0;
  created_ways := 0;
  fixed_segments := 0;

  CREATE OR REPLACE TEMP VIEW __isolated_ways AS
         SELECT w.*, a.the_geom AS source_geom, b.the_geom AS target_geom
         FROM ways w, ways_vertices_pgr a, ways_vertices_pgr b
         WHERE w.source = a.id AND a.cnt = 1 AND w.target = b.id AND b.cnt = 1;

  CREATE TEMP TABLE __isolated_nodes AS
         SELECT source AS id FROM __isolated_ways
         UNION ALL
         SELECT target AS id FROM __isolated_ways;

  FOR segment IN SELECT * FROM __isolated_ways LOOP
    isolated := isolated + 1;
    segment_fixed := FALSE;
    SELECT w.id, w.the_geom
           FROM ways_vertices_pgr w
           WHERE id NOT IN (SELECT id FROM __isolated_nodes)
           ORDER by w.the_geom <-> segment.source_geom
           LIMIT 1
           INTO closest_to_source;
    IF ST_Distance(segment.source_geom::geography, closest_to_source.the_geom::geography) < threshold THEN
       new_way_gid := insert_way(segment.class_id, segment.maxspeed_forward, segment.source, closest_to_source.id);
       created_ways := created_ways + 1;
       segment_fixed := TRUE;
    END IF;

    SELECT w.id, w.the_geom
           FROM ways_vertices_pgr w
           WHERE id NOT IN (SELECT id FROM __isolated_nodes)
           ORDER by w.the_geom <-> segment.target_geom
           LIMIT 1
           INTO closest_to_target;
    IF ST_Distance(segment.target_geom::geography, closest_to_target.the_geom::geography) < threshold THEN
       new_way_gid := insert_way(segment.class_id, segment.maxspeed_forward, segment.target, closest_to_target.id);
       created_ways := created_ways + 1;
       segment_fixed := TRUE;
    END IF;

    IF segment_fixed THEN
      fixed_segments := fixed_segments + 1;
    END IF;
  END LOOP;

  DROP TABLE __isolated_nodes CASCADE;
  DROP VIEW __isolated_ways CASCADE;

  RAISE NOTICE 'Fixed % segments from a total of % isolated by creating % new ways', fixed_segments, isolated, created_ways;
  RETURN fixed_segments;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION fixpoint_connect_isolated_segments (threshold float)
RETURNS void AS $$
DECLARE
  fixed_segments INTEGER;
BEGIN
  LOOP
    SELECT connect_isolated_segments(threshold) INTO fixed_segments;
    EXIT WHEN fixed_segments = 0;
  END LOOP;
  RAISE NOTICE 'ATTENTION: Remember to run apply_traffic_factor() to compute the costs of the new ways';
END;
$$ LANGUAGE plpgsql;
