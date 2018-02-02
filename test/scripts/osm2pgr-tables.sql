CREATE EXTENSION IF NOT EXISTS postgis;
CREATE EXTENSION IF NOT EXISTS pgrouting;

CREATE TABLE IF NOT EXISTS ways (
    gid bigint NOT NULL,
    class_id integer NOT NULL,
    length double precision,
    length_m double precision,
    name text,
    source bigint,
    target bigint,
    x1 double precision,
    y1 double precision,
    x2 double precision,
    y2 double precision,
    cost double precision,
    reverse_cost double precision,
    cost_s double precision,
    reverse_cost_s double precision,
    rule text,
    one_way integer,
    maxspeed_forward integer,
    maxspeed_backward integer,
    osm_id bigint,
    source_osm bigint,
    target_osm bigint,
    priority double precision DEFAULT 1,
    the_geom geometry(LineString,4326)
);

CREATE SEQUENCE IF NOT EXISTS ways_gid_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE ways_gid_seq OWNED BY ways.gid;

CREATE TABLE IF NOT EXISTS ways_vertices_pgr (
    id bigint NOT NULL,
    osm_id bigint,
    cnt integer,
    chk integer,
    ein integer,
    eout integer,
    lon numeric(11,8),
    lat numeric(11,8),
    the_geom geometry(Point,4326)
);

CREATE SEQUENCE IF NOT EXISTS ways_vertices_pgr_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE ways_vertices_pgr_id_seq OWNED BY ways_vertices_pgr.id;

ALTER TABLE ONLY ways ALTER COLUMN gid SET DEFAULT nextval('ways_gid_seq'::regclass);

ALTER TABLE ONLY ways_vertices_pgr ALTER COLUMN id SET DEFAULT nextval('ways_vertices_pgr_id_seq'::regclass);

