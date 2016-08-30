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
      (update :stats edn/read-string)
      (update :filters edn/read-string)))

(defn project->db
  "Performs the necessary transformations on a project to be used by the SQL
  functions"
  [project]
  (-> project
      (update :stats pr-str)
      (update :filters pr-str)))

;; Access related functions

(defn owned-by?
  [project user-id]
  (= user-id (:owner-id project)))

(defn- view-for-user
  "Removes sensitive data if the requesting user is not the project owner,
   and adds a read-only flag."
  [project requester-user-id]
  (if (or (nil? requester-user-id) (owned-by? project requester-user-id))
    project
    (-> project
      (dissoc :share-token)
      (assoc :read-only true))))

;; ----------------------------------------------------------------------
;; Service definition

(defrecord ProjectsService [db facilities])

(defn projects-service
  "Constructs a Projects service component"
  []
  (map->ProjectsService {}))


;; ----------------------------------------------------------------------
;; Service functions

(defn list-projects-for-dataset
  [service dataset-id]
  (->> (select-projects-for-dataset (get-db service) {:dataset-id dataset-id})
       (map db->project)))

(defn list-projects-for-user [service user-id]
  (->> (select-projects-for-user (get-db service) {:user-id user-id})
       (map db->project)
       (map #(view-for-user % user-id))))

(defn get-project
 ([service id]
  (get-project service id nil))
 ([service id user-id]
  (some-> (select-project (get-db service) {:id id, :user-id user-id})
          (db->project)
          (view-for-user user-id))))

(defn- facilities-criteria
  [project]
  {:region (:region-id project)
   :types (or (get-in project [:filters :facilities :type]) [])})

(defn compute-project-stats
  [{:keys [facilities]} project]
  (let [region-id (:region-id project)
        dataset-id (:dataset-id project)
        facilities-total (facilities/count-facilities facilities dataset-id {:region region-id})
        criteria (facilities-criteria project)
        facilities-targeted (facilities/count-facilities facilities dataset-id criteria)]
    {:facilities-targeted facilities-targeted
     :facilities-total facilities-total}))

(defn- load-project
  [db project-or-id]
  (if (map? project-or-id)
    project-or-id
    (-> (select-project db {:id project-or-id})
        db->project)))

(defn create-project [service project]
  (let [db (get-db service)
        stats (compute-project-stats service project)
        project-with-stats (assoc project :stats stats)
        project-id (->> project-with-stats
                        (project->db)
                        (insert-project! db)
                        (:id))
        project (load-project db project-id)]
    (assoc project :stats stats)))

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
          params     {:project-id project-id
                      :goal       goal
                      :filters    (some-> filters pr-str)}
          result     (update-project* tx params)]
      (when (= 1 result)
        (let [project (load-project tx project-id)]
          (if filters
            (let [stats (compute-project-stats service project)]
              (update-project* tx {:project-id project-id
                                   :stats (pr-str stats)})
              (assoc project :stats stats))
            project))))))

(defn delete-project [service id]
  (pos? (delete-project* (get-db service) {:id id})))

(defn list-project-shares
  [service]
  (list-project-shares* (get-db service)))

(defn create-project-share
  [service project-id token user-id]
  (let [project (load-project (get-db service) project-id)]
    (when (and project (= token (:share-token project)))
      (create-project-share! (get-db service) {:user-id user-id, :project-id project-id})
      (view-for-user project user-id))))

(defn delete-project-share
  [service project-id user-id]
  (pos? (delete-project-share* (get-db service) {:user-id user-id, :project-id project-id})))

(defn reset-share-token
  [service project-id]
  (:share-token (reset-share-token* (get-db service) {:id project-id})))
