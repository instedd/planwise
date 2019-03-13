(ns planwise.component.coverage.friction
  (:require [hugsql.core :as hugsql]
            [clojure.string :as str]
            [taoensso.timbre :as timbre]
            [planwise.util.geo :as geo]
            [planwise.boundary.runner :as runner])
  (:import [org.postgis PGgeometry]))

(timbre/refer-timbre)

(hugsql/def-db-fns "planwise/sql/coverage/friction.sql")

(defn find-friction-raster
  [db-spec coords]
  (let [pg-point (geo/make-pg-point coords)
        result   (find-country-region-with-point db-spec {:point pg-point})]
    (when-let [region-id (:id result)]
      ;; TODO: parameterize data folder
      (str "data/friction/regions/" region-id ".tif"))))

(defn compute-polygon
  [runner friction-raster coords max-time min-friction]
  (let [coords      (->> coords ((juxt :lon :lat)) (str/join ","))
        args        (map str ["-i" friction-raster "-m" max-time "-g" coords "-f" min-friction])
        polygon-wkt (runner/run-external runner :bin 2000 "walking-coverage" args)]
    (PGgeometry. (str "SRID=4326;" polygon-wkt))))
