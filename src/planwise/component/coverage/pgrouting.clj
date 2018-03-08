(ns planwise.component.coverage.pgrouting
  (:require [hugsql.core :as hugsql])
  (:import [org.postgis PGgeometry]))

(hugsql/def-db-fns "planwise/sql/coverage/pgrouting.sql")

(defn make-point
  ([{:keys [lat lon]}]
   (make-point lat lon))
  ([lat lon]
   (PGgeometry. (str "SRID=4326;POINT(" lon " " lat")"))))

(defn compute-coverage
  [db-spec point threshold]
  (compute-pgr-alpha-coverage db-spec {:point point :threshold threshold}))


;; REPL testing

(comment
  ;; Kilifi location
  (compute-coverage (:spec (planwise.repl/db)) (make-point -3.0361 40.1333) 180)

  ;; Nairobi location
  (compute-coverage (:spec (planwise.repl/db)) (make-point -1.2741 36.7931) 180))

