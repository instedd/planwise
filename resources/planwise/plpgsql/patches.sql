CREATE EXTENSION IF NOT EXISTS postgis;
CREATE EXTENSION IF NOT EXISTS pgrouting;

-- Copied from pgRouting and modified
-- Original file: src/alpha_shape/sql/alpha_shape.sql
-- Original copyright notice follows

/*PGR-GNU*****************************************************************

Copyright (c) 2015 Celia Virginia Vergara Castillo
Copyright (c) 2006-2007 Anton A. Patrushev, Orkney, Inc.
Copyright (c) 2005 Sylvain Pasche,
Mail: project@pgrouting.org

------

This program is free software; you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation; either version 2 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program; if not, write to the Free Software
Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.

********************************************************************PGR-GNU*/

CREATE OR REPLACE FUNCTION patched_pointsAsPolygon(query varchar)
  RETURNS geometry AS
  $$
  DECLARE
    r record;
    geoms geometry[];
    vertex_result record;
    i int;
    n int;
    spos int;
    q text;
    x float8[];
    y float8[];

  BEGIN
    geoms := array[]::geometry[];
    i := 1;

    FOR vertex_result IN EXECUTE 'SELECT x, y FROM pgr_alphashape('''|| query || ''', 0)'
    LOOP
      x[i] = vertex_result.x;
      y[i] = vertex_result.y;
      i := i+1;
    END LOOP;

    n := i;
    IF n = 1 THEN
      RAISE NOTICE 'n = 1';
      RETURN NULL;
    END IF;

    spos := 1;
    q := 'SELECT ST_GeometryFromText(''POLYGON((';
    FOR i IN 1..n LOOP
      IF x[i] IS NULL AND y[i] IS NULL THEN
        q := q || ', ' || x[spos] || ' ' || y[spos] || '))'',0) AS geom;';
        EXECUTE q INTO r;
        geoms := geoms || array[r.geom];
        q := '';
      ELSE
        IF q = '' THEN
          spos := i;
          q := 'SELECT ST_GeometryFromText(''POLYGON((';
        END IF;
        IF i = spos THEN
          q := q || x[spos] || ' ' || y[spos];
        ELSE
          q := q || ', ' || x[i] || ' ' || y[i];
        END IF;
      END IF;
    END LOOP;

    -- If the number of geometries is just one, simply return the polygon as is
    -- This fixes some cases where the computed polygons from pgr_alphaShape are
    -- not proper (ie. they have closed holes but not as inner rings) which
    -- ST_BuildArea doesn't like
    IF cardinality(geoms) = 1 THEN
      RETURN geoms[1];
    ELSE
      RETURN ST_BuildArea(ST_Collect(geoms));
    END IF;
  END;
  $$
  LANGUAGE 'plpgsql' VOLATILE STRICT;
