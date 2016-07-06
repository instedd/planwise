(ns planwise.component.projects
  (:require [clojure.java.jdbc :as jdbc]
            [com.stuartsierra.component :as component]
            [hugsql.core :as hugsql]))

;; ----------------------------------------------------------------------
;; Auxiliary and utility functions

(hugsql/def-db-fns "planwise/sql/projects.sql")

(defn get-db [service]
  (get-in service [:db :spec]))


;; ----------------------------------------------------------------------
;; Service definition

(defrecord ProjectsService [db])

(defn projects-service []
  "Constructs a Projects service component"
  (map->ProjectsService {}))


;; ----------------------------------------------------------------------
;; Service functions

(defn list-projects [service]
  (select-projects (get-db service)))

(defn create-project [service project]
  (let [db (get-db service)
        id (-> (insert-project! db project) (first) (:id))]
    (assoc project :id id)))
