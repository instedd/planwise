-- Creates a new table `ways_nodes` which contains all the points for all the
-- `ways` table. This *will* produce duplicate nodes as it is.

DROP TABLE IF EXISTS ways_nodes;
DROP SEQUENCE IF EXISTS ways_nodes_id;

CREATE TABLE ways_nodes (
id SERIAL,
gid BIGINT NOT NULL,
lon NUMERIC(11,8),
lat NUMERIC(11,8));

CREATE INDEX ways_nodes_gid ON ways_nodes(gid);

INSERT INTO ways_nodes (gid, lon, lat)
SELECT
gid,
ST_x(ST_PointN(the_geom, g.generate_series)) AS lon,
ST_y(ST_PointN(the_geom, g.generate_series)) AS lat
FROM
ways,
(SELECT generate_series(1, (SELECT MAX(ST_NPoints(the_geom)) FROM ways))) g
WHERE g.generate_series <= ST_NPoints(the_geom);
