(ns planwise.component.analyses
  (:require [com.stuartsierra.component :as component]
            [taoensso.timbre :as timbre]
            [clojure.java.jdbc :as jdbc]
            [hugsql.core :as hugsql]))

(timbre/refer-timbre)

;; ----------------------------------------------------------------------
;; Auxiliary and utility functions

(hugsql/def-db-fns "planwise/sql/analyses.sql")

(defn get-db
  "Retrieve the database connection for a service"
  [component]
  (get-in component [:db :spec]))


;; ----------------------------------------------------------------------
;; Component definition

(defrecord AnalysesStore [db])

(defn analyses-store
  "Construct a Analyses store component"
  []
  (map->AnalysesStore {}))

(defn select-analyses-for-user
  [store user-id]
  (select-analyses (get-db store) {:owner-id user-id}))

(defn create-analysis-for-user!
  [store user-id]
  (create-analysis! (get-db store) {:owner-id user-id :name "New Analysis"}))

(defn find-analysis
  [store id]
  (select-analysis (get-db store) {:id id}))
