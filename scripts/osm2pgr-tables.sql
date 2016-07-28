--
-- PostgreSQL database dump
--

-- Dumped from database version 9.5.2
-- Dumped by pg_dump version 9.5.2

SET statement_timeout = 0;
SET lock_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SET check_function_bodies = false;
SET client_min_messages = warning;
SET row_security = off;

SET search_path = public, pg_catalog;

SET default_tablespace = '';

SET default_with_oids = false;

CREATE EXTENSION IF NOT EXISTS postgis;
CREATE EXTENSION IF NOT EXISTS pgrouting;
--
-- Name: ways; Type: TABLE; Schema: public; Owner: ggiraldez
--

CREATE TABLE ways (
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


ALTER TABLE ways OWNER TO ggiraldez;

--
-- Name: ways_gid_seq; Type: SEQUENCE; Schema: public; Owner: ggiraldez
--

CREATE SEQUENCE ways_gid_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER TABLE ways_gid_seq OWNER TO ggiraldez;

--
-- Name: ways_gid_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: ggiraldez
--

ALTER SEQUENCE ways_gid_seq OWNED BY ways.gid;


--
-- Name: ways_vertices_pgr; Type: TABLE; Schema: public; Owner: ggiraldez
--

CREATE TABLE ways_vertices_pgr (
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


ALTER TABLE ways_vertices_pgr OWNER TO ggiraldez;

--
-- Name: ways_vertices_pgr_id_seq; Type: SEQUENCE; Schema: public; Owner: ggiraldez
--

CREATE SEQUENCE ways_vertices_pgr_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER TABLE ways_vertices_pgr_id_seq OWNER TO ggiraldez;

--
-- Name: ways_vertices_pgr_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: ggiraldez
--

ALTER SEQUENCE ways_vertices_pgr_id_seq OWNED BY ways_vertices_pgr.id;


--
-- Name: gid; Type: DEFAULT; Schema: public; Owner: ggiraldez
--

ALTER TABLE ONLY ways ALTER COLUMN gid SET DEFAULT nextval('ways_gid_seq'::regclass);


--
-- Name: id; Type: DEFAULT; Schema: public; Owner: ggiraldez
--

ALTER TABLE ONLY ways_vertices_pgr ALTER COLUMN id SET DEFAULT nextval('ways_vertices_pgr_id_seq'::regclass);


--
-- Name: vertex_id; Type: CONSTRAINT; Schema: public; Owner: ggiraldez
--

ALTER TABLE ONLY ways_vertices_pgr
    ADD CONSTRAINT vertex_id UNIQUE (osm_id);


--
-- Name: ways_pkey; Type: CONSTRAINT; Schema: public; Owner: ggiraldez
--

ALTER TABLE ONLY ways
    ADD CONSTRAINT ways_pkey PRIMARY KEY (gid);


--
-- Name: ways_vertices_pgr_pkey; Type: CONSTRAINT; Schema: public; Owner: ggiraldez
--

ALTER TABLE ONLY ways_vertices_pgr
    ADD CONSTRAINT ways_vertices_pgr_pkey PRIMARY KEY (id);


--
-- Name: ways_gdx; Type: INDEX; Schema: public; Owner: ggiraldez
--

CREATE INDEX ways_gdx ON ways USING gist (the_geom);


--
-- Name: ways_source_idx; Type: INDEX; Schema: public; Owner: ggiraldez
--

CREATE INDEX ways_source_idx ON ways USING btree (source);


--
-- Name: ways_source_osm_idx; Type: INDEX; Schema: public; Owner: ggiraldez
--

CREATE INDEX ways_source_osm_idx ON ways USING btree (source_osm);


--
-- Name: ways_target_idx; Type: INDEX; Schema: public; Owner: ggiraldez
--

CREATE INDEX ways_target_idx ON ways USING btree (target);


--
-- Name: ways_target_osm_idx; Type: INDEX; Schema: public; Owner: ggiraldez
--

CREATE INDEX ways_target_osm_idx ON ways USING btree (target_osm);


--
-- Name: ways_vertices_pgr_gdx; Type: INDEX; Schema: public; Owner: ggiraldez
--

CREATE INDEX ways_vertices_pgr_gdx ON ways_vertices_pgr USING gist (the_geom);


--
-- Name: ways_vertices_pgr_osm_id_idx; Type: INDEX; Schema: public; Owner: ggiraldez
--

CREATE INDEX ways_vertices_pgr_osm_id_idx ON ways_vertices_pgr USING btree (osm_id);


--
-- PostgreSQL database dump complete
--

