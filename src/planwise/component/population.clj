(ns planwise.component.population
  (:require [integrant.core :as ig]
            [taoensso.timbre :as timbre]
            [hugsql.core :as hugsql]))

;; ----------------------------------------------------------------------
;; Auxiliary and utility functions

(hugsql/def-db-fns "planwise/sql/population.sql")

(defn get-db
  [store]
  (get-in store [:db :spec]))

;; ----------------------------------------------------------------------
;; Service definition

(defrecord PopulationStore [db])

(defn list-population-sources
  [store]
  (db-list-population-sources (get-db store)))

(defmethod ig/init-key :planwise.component/population
  [_ config]
  (map->PopulationStore config))
