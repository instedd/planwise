(ns planwise.component.projects2
  (:require [planwise.boundary.projects2 :as boundary]
            [planwise.boundary.scenarios :as scenarios]
            [planwise.component.regions :as regions]
            [integrant.core :as ig]
            [taoensso.timbre :as timbre]
            [clojure.java.jdbc :as jdbc]
            [hugsql.core :as hugsql]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :refer [blank?]]
            [planwise.model.projects2 :as model]
            [planwise.util.hash :refer [update*]]))

;; ----------------------------------------------------------------------
;; Auxiliary and utility functions

(hugsql/def-db-fns "planwise/sql/projects2.sql")

(defn get-db
  [store]
  (get-in store [:db :spec]))

(defn- ^boolean valid-goal?
  [project]
  (every? false? [(blank? (:name project))
                  (nil? (:region-id project))]))

(defn- ^boolean valid-demographics?
  [project]
  (let [demographics (get-in project [:config :demographics])]
    (every? false? [(nil?   (:population-source-id project))
                    (blank? (:unit-name demographics))
                    (nil? (:target demographics))])))

(defn- ^boolean valid-sites?
  [project]
  (let [sites (get-in project [:config :sites])]
    (every? false? [(nil? (:dataset-id project))
                    (nil? (:capacity sites))])))

(defn- ^boolean valid-coverage?
  [project]
  (let [coverage (get-in project [:config :coverage])]
    (not (empty? (:filter-options coverage)))))

(defn- ^boolean valid-actions?
  [project]
  (let [actions (get-in project [:config :actions])]
    (every? false? [(empty? actions)
                    (nil? (:budget actions))])))

(defn- ^boolean valid-project?
  [project]
  (every? #(% project) [valid-goal?
                        valid-demographics?
                        valid-sites?
                        valid-coverage?
                        valid-actions?]))

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
  (regions/db->region
   (-> (db-get-project (get-db store) {:id project-id})
       (update* :engine-config edn/read-string)
       (update* :config edn/read-string)
       (update* :config model/apply-default))))

(defn list-projects
  [store owner-id]
  (db-list-projects (get-db store) {:owner-id owner-id}))

(defn start-project
  [store project-id]
  (let [project (get-project store project-id)]
    (when (valid-project? project)
      (db-start-project! (get-db store) {:id project-id})
      (scenarios/create-initial-scenario (:scenarios store) project))))

(defn reset-project
  [store project-id]
  (db-reset-project! (get-db store) {:id project-id})
  (scenarios/reset-scenarios (:scenarios store) project-id))
  ;; TODO should increment some incarnation to dismiss ongoing deferred jobs

(defn delete-project
  [store project-id]
  (db-delete-project! (get-db store) {:id project-id}))

(defrecord SitesProjectsStore [db scenarios]
  boundary/Projects2
  (create-project [store owner-id]
    (create-project store owner-id))
  (list-projects [store owner-id]
    (list-projects store owner-id))
  (get-project [store project-id]
    (get-project store project-id))
  (update-project [store project]
    (update-project store project))
  (start-project [store project-id]
    (start-project store project-id))
  (reset-project [store project-id]
    (reset-project store project-id))
  (delete-project [store project-id]
    (delete-project store project-id)))

(defmethod ig/init-key :planwise.component/projects2
  [_ config]
  (map->SitesProjectsStore config))

(comment
  ;; REPL testing

  (def store (:planwise.component/projects2 integrant.repl.state/system))
  (start-project store 5))
