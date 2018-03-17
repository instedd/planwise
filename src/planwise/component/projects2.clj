(ns planwise.component.projects2
  (:require [integrant.core :as ig]
            [taoensso.timbre :as timbre]
            [clojure.data.csv :as csv]
            [clojure.java.jdbc :as jdbc]
            [hugsql.core :as hugsql]
            [clojure.edn :as edn]
            [clojure.java.io :as io]))

;; ----------------------------------------------------------------------
;; Auxiliary and utility functions

(hugsql/def-db-fns "planwise/sql/projects2.sql")

(defn get-db
  [store]
  (get-in store [:db :spec]))
;----for PUT request
(defn project-config->edn
  [project-config]
  (prn-str project-config))
;----for GET request
(defn edn->project-config
  [edn-data]
  (edn/read-string edn-data))

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

(defn list-projects
  [store owner-id]
  (db-list-projects (get-db store) {:owner-id owner-id}))
;; ----------------------------------------------------------------------
;; Adding config to project

(defn add-project-config
  [store project-config project-id]
  (db-add-config! (get-db store) {:id project-id
                                  :config (project-config->edn project-config)}))


(defmethod ig/init-key :planwise.component/projects2
  [_ config]
  (map->SitesProjectsStore config))
