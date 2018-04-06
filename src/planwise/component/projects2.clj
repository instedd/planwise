(ns planwise.component.projects2
  (:require [planwise.boundary.projects2 :as boundary]
            [integrant.core :as ig]
            [taoensso.timbre :as timbre]
            [clojure.data.csv :as csv]
            [clojure.java.jdbc :as jdbc]
            [hugsql.core :as hugsql]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [planwise.model.projects2 :as model]
            [planwise.util.hash :refer [update*]]))

;; ----------------------------------------------------------------------
;; Auxiliary and utility functions

(hugsql/def-db-fns "planwise/sql/projects2.sql")

(defn get-db
  [store]
  (get-in store [:db :spec]))

;; ----------------------------------------------------------------------
;; Service definition

(defn create-project
  [store owner-id]
  (db-create-project! (get-db store) {:owner-id owner-id
                                      :name ""
                                      :state "draft"}))

(defn update-project
  [store project]
  (db-update-project (get-db store) (-> project
                                        (update :config pr-str)
                                        (dissoc :state))))

(defn get-project
  [store project-id]
  (-> (db-get-project (get-db store) {:id project-id})
      (update* :config edn/read-string)
      (update* :config model/apply-default)))

(defn list-projects
  [store owner-id]
  (db-list-projects (get-db store) {:owner-id owner-id}))

(defn start-project
  [store project-id]
  (db-update-state-project (get-db store) {:id project-id
                                           :state "started"}))

(defrecord SitesProjectsStore [db]
  boundary/Projects2
  (create-project [this user-id]
    (create-project this user-id))
  (list-projects [this user-id]
    (list-projects this user-id))
  (get-project [this project-id]
    (get-project [this project-id]))
  (update-project [this project]
    (update-project [this project]))
  (start-project [this project-id]
    (start-project this project-id)))

(defn projects2-store
  "Constructs a Projects2 Store component"
  []
  (map->SitesProjectsStore {}))

(defmethod ig/init-key :planwise.component/projects2
  [_ config]
  (map->SitesProjectsStore config))
