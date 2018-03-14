(ns planwise.util.pg
  (:require [clojure.spec.alpha :as s])
  (:import [org.postgis PGgeometry Geometry]))

(s/def ::lat number?)
(s/def ::lon number?)
(s/def ::coords (s/keys :req-un [::lat ::lon]))

(s/def ::geometry #(instance? PGgeometry %))
(s/def ::point   (s/and ::geometry #(= Geometry/POINT   (.getGeoType %))))
(s/def ::polygon (s/and ::geometry #(= Geometry/POLYGON (.getGeoType %))))

(defn make-point*
  [lat lon]
  (PGgeometry. (str "SRID=4326;POINT(" lon " " lat ")")))

(defn make-point
  ([{:keys [lat lon] :as coords}]
   {:pre [(s/valid? ::coords coords)]}
   (make-point lat lon))
  ([lat lon]
   {:pre [(s/valid? ::lat lat)
          (s/valid? ::lon lon)]
    :post [(s/valid? ::point %)]}
   (make-point* lat lon)))

