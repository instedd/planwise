(ns planwise.component.datasets
  (:require [com.stuartsierra.component :as component]
            [taoensso.timbre :as timbre]
            [hugsql.core :as hugsql]))

;; ----------------------------------------------------------------------
;; Auxiliary and utility functions

(hugsql/def-db-fns "planwise/sql/datasets.sql")

(defn get-db [store]
  (get-in store [:db :spec]))


;; ----------------------------------------------------------------------
;; Service definition

(defrecord DatasetsStore [db])

(defn datasets-store
  "Constructs a Datasets Store component"
  []
  (map->DatasetsStore {}))

(defn list-datasets-for-user
  [store user-id]
  (select-datasets-for-user (get-db store) {:user-id user-id}))
