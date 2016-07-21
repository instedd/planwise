(ns planwise.component.projects
  (:require [planwise.component.facilities :as facilities]
            [clojure.java.jdbc :as jdbc]
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

(defn projects-service
  "Constructs a Projects service component"
  []
  (map->ProjectsService {}))


;; ----------------------------------------------------------------------
;; Service functions

(defn list-projects [service]
  (select-projects (get-db service)))

(defn get-project [service id]
  (select-project (get-db service) {:id id}))

(defn create-project [service project]
  (let [db (get-db service)
        facilities_count (facilities/count-facilities-in-region service {:region (:region_id project)})
        project-ready (assoc project :facilities_count (:count facilities_count))
        id (-> (insert-project! db project-ready) (first) (:id))]
    (assoc project-ready :id id)))
