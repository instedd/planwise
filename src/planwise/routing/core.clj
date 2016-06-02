(ns planwise.routing.core
  (:require [clojure.java.jdbc :as jdbc]
            [hugsql.core :as hugsql]))

(hugsql/def-db-fns "planwise/routing/routing.sql")

;; (get-nearest-node-sqlvec {:lat 10 :lon 20})
;; (get-nearest-node routing-db {:lat -1.3 :lon 36})

(defn nearest-node [db lat lon]
  (get-nearest-node db {:lat lat :lon lon}))

;; (isochrone-for-node-sqlvec {:node-id 1657 :distance 10000})
;; (isochrone-for-node routing-db {:node-id 1657 :distance 10000})

(defn isochrone [db id distance]
  (:poly (isochrone-for-node db {:node-id id :distance distance})))
