(ns planwise.component.projects2
  (:require [integrant.core :as ig]
            [taoensso.timbre :as timbre]
            [clojure.data.csv :as csv]
            [clojure.java.jdbc :as jdbc]
            [hugsql.core :as hugsql]
            [clojure.edn :as edn]
            [planwise.util.hash :refer [update-if]]
            [clojure.java.io :as io]))

;; ----------------------------------------------------------------------
;; Auxiliary and utility functions

(hugsql/def-db-fns "planwise/sql/projects2.sql")

(defn get-db
  [store]
  (get-in store [:db :spec]))

;; ----------------------------------------------------------------------
;; Service definition

(defrecord SitesProjectsStore [db])

(defn projects2-store
  "Constructs a Projects2 Store component"
  []
  (map->SitesProjectsStore {}))

(defn create-project
  [store owner-id]
  (db-create-project! (get-db store) {:owner-id owner-id
                                      :name ""}))

(defn update-project
  [store project-id name]
  (db-update-project (get-db store) {:id project-id
                                     :name name}))

(defn get-project
  [store project-id]
  (db-get-project (get-db store) {:id project-id}))        

(defmethod ig/init-key :planwise.component/projects2
  [_ config]
  (map->SitesProjectsStore config))
