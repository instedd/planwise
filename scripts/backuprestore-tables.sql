--
-- PostgreSQL database dump
--

-- Dumped from database version 9.5.3
-- Dumped by pg_dump version 9.5.3

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

--
-- Name: facilities_polygons; Type: TABLE; Schema: public; Owner: planwise
--

CREATE TABLE facilities_polygons (
    facility_id integer NOT NULL,
    threshold integer NOT NULL,
    method character varying NOT NULL,
    the_geom geometry(Polygon,4326),
    starting_node integer,
    id bigint NOT NULL,
    area double precision,
    population integer
);


ALTER TABLE facilities_polygons OWNER TO planwise;

--
-- Name: facilities_polygons_id_seq; Type: SEQUENCE; Schema: public; Owner: planwise
--

CREATE SEQUENCE facilities_polygons_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER TABLE facilities_polygons_id_seq OWNER TO planwise;

--
-- Name: facilities_polygons_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: planwise
--

ALTER SEQUENCE facilities_polygons_id_seq OWNED BY facilities_polygons.id;


--
-- Name: facilities_polygons_regions; Type: TABLE; Schema: public; Owner: planwise
--

CREATE TABLE facilities_polygons_regions (
    facility_polygon_id bigint,
    region_id integer,
    area double precision,
    population integer
);


ALTER TABLE facilities_polygons_regions OWNER TO planwise;

--
-- Name: ways_nodes; Type: TABLE; Schema: public; Owner: planwise
--

CREATE TABLE ways_nodes (
    id integer NOT NULL,
    gid bigint NOT NULL,
    lon numeric(11,8),
    lat numeric(11,8)
);


ALTER TABLE ways_nodes OWNER TO planwise;

--
-- Name: ways_nodes_id_seq; Type: SEQUENCE; Schema: public; Owner: planwise
--

CREATE SEQUENCE ways_nodes_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER TABLE ways_nodes_id_seq OWNER TO planwise;

--
-- Name: ways_nodes_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: planwise
--

ALTER SEQUENCE ways_nodes_id_seq OWNED BY ways_nodes.id;


--
-- Name: id; Type: DEFAULT; Schema: public; Owner: planwise
--

ALTER TABLE ONLY facilities_polygons ALTER COLUMN id SET DEFAULT nextval('facilities_polygons_id_seq'::regclass);


--
-- Name: id; Type: DEFAULT; Schema: public; Owner: planwise
--

ALTER TABLE ONLY ways_nodes ALTER COLUMN id SET DEFAULT nextval('ways_nodes_id_seq'::regclass);


--
-- Name: facilities_polygons_pkey; Type: CONSTRAINT; Schema: public; Owner: planwise
--

ALTER TABLE ONLY facilities_polygons
    ADD CONSTRAINT facilities_polygons_pkey PRIMARY KEY (id);


--
-- Name: facilities_polygons_facility_method_threshold; Type: INDEX; Schema: public; Owner: planwise
--

CREATE INDEX facilities_polygons_facility_method_threshold ON facilities_polygons USING btree (facility_id, method, threshold);


--
-- Name: ways_nodes_gid; Type: INDEX; Schema: public; Owner: planwise
--

CREATE INDEX ways_nodes_gid ON ways_nodes USING btree (gid);


--
-- Name: facilities_polygons_facility_id_pkey; Type: FK CONSTRAINT; Schema: public; Owner: planwise
--

ALTER TABLE ONLY facilities_polygons
    ADD CONSTRAINT facilities_polygons_facility_id_pkey FOREIGN KEY (facility_id) REFERENCES facilities(id) ON DELETE CASCADE;


--
-- Name: facilities_polygons_regions_facility_polygon_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: planwise
--

ALTER TABLE ONLY facilities_polygons_regions
    ADD CONSTRAINT facilities_polygons_regions_facility_polygon_id_fkey FOREIGN KEY (facility_polygon_id) REFERENCES facilities_polygons(id) ON DELETE CASCADE;


--
-- Name: facilities_polygons_regions_region_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: planwise
--

ALTER TABLE ONLY facilities_polygons_regions
    ADD CONSTRAINT facilities_polygons_regions_region_id_fkey FOREIGN KEY (region_id) REFERENCES regions(id) ON DELETE CASCADE;


--
-- PostgreSQL database dump complete
--
