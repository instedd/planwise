(ns planwise.component.routing
  (:require [com.stuartsierra.component :as component]
            [clojure.java.jdbc :as jdbc]
            [hugsql.core :as hugsql]))

;; ----------------------------------------------------------------------
;; Auxiliary and utility functions

(hugsql/def-db-fns "planwise/sql/routing.sql")

(defn get-db [service]
  (get-in service [:db :spec]))


;; ----------------------------------------------------------------------
;; Service definition

(defrecord RoutingService [db])

(defn routing-service []
  "Constructs a Routing service component"
  (map->RoutingService {}))


;; ----------------------------------------------------------------------
;; Service functions

(defn nearest-node [service lat lon]
  (get-nearest-node (get-db service) {:lat lat :lon lon}))

(defn isochrone [service id distance & [algorithm]]
  (let [algorithm (or algorithm :alpha-shape)]
    (let [func (condp = algorithm
                 :alpha-shape isochrone-for-node-alpha-shape
                 :buffer isochrone-for-node-buffer
                 (throw (IllegalArgumentException. (str "Invalid algorithm " algorithm))))]
      (:poly (func (get-db service) {:node-id id :distance distance})))))
