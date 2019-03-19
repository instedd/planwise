-- :name db-inside-geometry :? :1
SELECT ST_Within(ST_SetSRID(ST_MakePoint(:lon,:lat),4326), :geom) AS cond;

-- :name db-get-max-distance :? :1
SELECT ST_MaxDistance(:geom, :geom) AS maxdist;

-- :name db-intersected-coverage-region :? :1
SELECT
    St_AsGEOJSON(St_Intersection(St_MakeValid(:geom), regions.the_geom)) AS geom
    FROM regions
    WHERE regions.id = :region-id;
