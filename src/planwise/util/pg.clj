(ns planwise.util.pg
  (:import [org.postgis PGgeometry]))

(defn make-point
  ([{:keys [lat lon]}]
   (make-point lat lon))
  ([lat lon]
   (PGgeometry. (str "SRID=4326;POINT(" lon " " lat")"))))
