(ns planwise.test-utils
  (:import [org.postgis PGgeometry]))

(defn sample-polygon []
  (PGgeometry. (str "SRID=4326;MULTIPOLYGON(((1 1, 1 2, 2 2, 2 1, 1 1)))")))
