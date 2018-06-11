(ns planwise.component.sources
  (:require [integrant.core :as ig]
            [taoensso.timbre :as timbre]
            [hugsql.core :as hugsql]))

;; ----------------------------------------------------------------------
;; Auxiliary and utility functions

(hugsql/def-db-fns "planwise/sql/sources.sql")

(defn get-db
  [store]
  (get-in store [:db :spec]))

;; ----------------------------------------------------------------------
;; Service definition

(defrecord SourcesStore [db])

(defn list-sources
  [store]
  (db-list-sources (get-db store)))

(defmethod ig/init-key :planwise.component/sources
  [_ config]
  (map->SourcesStore config))
