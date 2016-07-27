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
        facilities-count (facilities/count-facilities-in-region service (:region-id project))
        project-ready (assoc project :facilities-count facilities-count)
        project-id (-> (insert-project! db project-ready)
                       (:id))]
    (assoc project-ready :id project-id)))

(defn update-project-stats
  [service project]
  (jdbc/with-db-transaction [tx (get-db service)]
    (when-let [project (if (map? project)
                         project
                         (select-project tx {:id project}))]
      (let [project-id (:id project)
            region-id (:region_id project)
            facilities-count (facilities/count-facilities-in-region service region-id)]
        (update-project* tx {:project-id project-id
                             :facilities-count facilities-count})))))

(defn delete-project [service id]
  (pos? (delete-project* (get-db service) {:id id})))
