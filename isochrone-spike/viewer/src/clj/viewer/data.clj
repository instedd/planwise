(ns viewer.data
  (:require [clojure.java.jdbc :as jdbc]))

(def routing-db {:subprotocol "postgresql"
                 :subname "//localhost:5432/routing"})

(defn nearest-node [lat lon radius]
  (first (jdbc/query routing-db
                     ["SELECT id, st_asgeojson(the_geom) AS point, st_distance(the_geom, st_setsrid(st_makepoint(?, ?), 4326)) AS distance FROM ways_vertices_pgr WHERE st_within(the_geom, st_buffer(st_setsrid(st_makepoint(?, ?), 4326), ?)) ORDER BY distance LIMIT 1" lon lat lon lat radius])))

;; FIXME: pass the query parameters as JDBC parameters instead of building the
;; query by interpolating
(defn isochrone [id distance]
  (first (jdbc/query routing-db
                     [(str "SELECT st_asgeojson(pgr_pointsaspolygon('SELECT id::integer, st_x(the_geom)::float AS x, st_y(the_geom)::float AS y FROM ways_vertices_pgr WHERE id IN (SELECT node FROM pgr_drivingdistance(''''SELECT gid AS id, source, target, to_cost AS cost FROM ways'''', " id ", " distance "))', 0)) AS poly")]
                     {:row-fn :poly})))
