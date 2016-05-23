(ns viewer.routing
  (:require [clojure.java.jdbc :as jdbc]
            [hugsql.core :as hugsql]))

(hugsql/def-db-fns "sql/routing.sql")
;; (hugsql/def-sqlvec-fns "sql/routing.sql")

(def routing-db {:subprotocol "postgresql"
                 :subname "//localhost:5432/routing"})

;; (get-nearest-node-sqlvec {:lat 10 :lon 20})
;; (get-nearest-node routing-db {:lat -1.3 :lon 36})

(defn nearest-node [lat lon]
  (get-nearest-node routing-db {:lat lat :lon lon}))

;; (isochrone-for-node-sqlvec {:node-id 1657 :distance 10000})
;; (isochrone-for-node routing-db {:node-id 1657 :distance 10000})

(defn isochrone [id distance]
  (:poly (isochrone-for-node routing-db {:node-id id :distance distance})))
