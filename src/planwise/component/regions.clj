(ns planwise.component.regions
  (:require [clojure.java.jdbc :as jdbc]
            [com.stuartsierra.component :as component]
            [hugsql.core :as hugsql]))

;; ----------------------------------------------------------------------
;; Auxiliary and utility functions

(hugsql/def-db-fns "planwise/sql/regions.sql")

(defn get-db [service]
  (get-in service [:db :spec]))


;; ----------------------------------------------------------------------
;; Service definition

(defrecord RegionsService [db])

(defn regions-service
  "Constructs a Regions service component"
  []
  (map->RegionsService {}))


;; ----------------------------------------------------------------------
;; Service functions

(defn list-regions [service]
  (select-regions (get-db service)))

(defn list-regions-with-geo [service ids simplify]
  (select-regions-with-geo-given-ids (get-db service) {:ids ids, :simplify simplify}))
