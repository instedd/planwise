(ns planwise.component.projects
  (:require [planwise.component.facilities :as facilities]
            [clojure.java.jdbc :as jdbc]
            [com.stuartsierra.component :as component]
            [hugsql.core :as hugsql]
            [clojure.edn :as edn]
            [clojure.set :as set]))

;; ----------------------------------------------------------------------
;; Auxiliary and utility functions

(hugsql/def-db-fns "planwise/sql/projects.sql")

(defn get-db [service]
  (get-in service [:db :spec]))

;; Mapper functions

(defn db->project
  "Transforms a record retrieved from the database to make it usable from inside
  de application"
  [record]
  (-> record
      (set/rename-keys {:region_id :region-id
                        :region_name :region-name})
      (update :stats edn/read-string)
      (update :filters edn/read-string)))

(defn project->db
  "Performs the necessary transformations on a project to be used by the SQL
  functions"
  [project]
  (-> project
      (update :stats pr-str)
      (update :filters pr-str)))


;; ----------------------------------------------------------------------
;; Service definition

(defrecord ProjectsService [db facilities])

(defn projects-service
  "Constructs a Projects service component"
  []
  (map->ProjectsService {}))


;; ----------------------------------------------------------------------
;; Service functions

(defn list-projects [service]
  (->> (select-projects (get-db service))
       (map db->project)))

(defn get-project [service id]
  (-> (select-project (get-db service) {:id id})
      db->project))

(defn compute-project-stats
  [{:keys [facilities]} project]
  (let [region-id (:region-id project)
        facilities-total (facilities/count-facilities-in-region facilities region-id)]
    {:facilities-targeted 0
     :facilities-total facilities-total}))

(defn create-project [service project]
  (let [db (get-db service)
        stats (compute-project-stats service project)
        project-with-stats (assoc project :stats stats)
        project-id (->> project-with-stats
                        (project->db)
                        (insert-project! db)
                        (:id))]
    (assoc project-with-stats :id project-id)))

(defn- load-project
  [db project-or-id]
  (if (map? project-or-id)
    project-or-id
    (-> (select-project db {:id project-or-id})
        db->project)))

(defn update-project-stats
  [service project]
  (jdbc/with-db-transaction [tx (get-db service)]
    (when-let [project (load-project tx project)]
      (let [project-id (:id project)
            stats (compute-project-stats service project)]
        (update-project* tx {:project-id project-id
                             :stats (pr-str stats)})))))

(defn update-project
  [service project]
  (jdbc/with-db-transaction [tx (get-db service)]
    (let [project-id (:id project)
          goal       (:goal project)
          filters    (:filters project)
          stats      (:stats project)
          params     {:project-id project-id
                      :goal       goal
                      :filters    (some-> filters pr-str)
                      :stats      (some-> stats pr-str)}
          result     (update-project* tx params)]
      (when (= 1 result)
        (load-project tx project-id)))))
