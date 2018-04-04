(ns planwise.test-utils
  (:import [org.postgis PGgeometry]))

(defn sample-polygon
  ([]
   (sample-polygon :small))
  ([kind]
   (case kind
     :small (PGgeometry. (str "SRID=4326;MULTIPOLYGON(((1 1, 1 2, 2 2, 2 1, 1 1)))"))
     :large (PGgeometry. (str "SRID=4326;MULTIPOLYGON(((-30 -30, -30 60, 60 60, 60 -30, -30 -30)))")))))

(defn make-point [lat lon]
  (PGgeometry. (str "SRID=4326;POINT(" lon " " lat ")")))
