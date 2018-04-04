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

(defn project-config->edn
  [project-config]
  (prn-str project-config))

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
  [store project-id data]
  (let [edn-config (project-config->edn (:config data))]
    (db-update-project (get-db store) (assoc data :config edn-config))))

(defn get-project
  [store project-id]
  (let [project (db-get-project (get-db store) {:id project-id})
        config  (edn->project-config (:config (first project)))]
    (assoc (first project) :config config)))

(defn list-projects
  [store owner-id]
  (db-list-projects (get-db store) {:owner-id owner-id}))

(defmethod ig/init-key :planwise.component/projects2
  [_ config]
  (map->SitesProjectsStore config))
