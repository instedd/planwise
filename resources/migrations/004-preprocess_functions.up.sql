CREATE TABLE facilities_polygons (
        facility_id integer not null,
        threshold   integer not null,
        method      varchar not null,
        the_geom    geometry(polygon, 4326)
);
