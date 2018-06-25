(ns planwise.component.sources
  (:require [planwise.boundary.sources :as boundary]
            [integrant.core :as ig]
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

(defn list-sources
  [store owner-id]
  (db-list-sources (get-db store) {:owner-id owner-id}))

(defn import-from-csv
  [store {:keys [name owner-id]} csv-file])

(defrecord SourcesStore [db]
  boundary/Sources
  (list-sources [store owner-id]
    (list-sources [store owner-id]))
  (import-from-csv [store options csv-file]
    (import-from-csv [store options csv-file])))

(defmethod ig/init-key :planwise.component/sources
  [_ config]
  (map->SourcesStore config))