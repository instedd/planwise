-- Creates a new table `ways_nodes` which contains all the points for all the
-- `ways` table. This *will* produce duplicate nodes as it is.

CREATE TABLE ways_nodes (
id SERIAL,
gid BIGINT NOT NULL,
lon NUMERIC(11,8),
lat NUMERIC(11,8));

CREATE INDEX ways_nodes_gid ON ways_nodes(gid);
