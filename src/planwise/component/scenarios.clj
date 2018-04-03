(ns planwise.component.scenarios
  (:require [planwise.boundary.scenarios :as boundary]
            [planwise.model.scenarios :as model]
            [integrant.core :as ig]
            [taoensso.timbre :as timbre]
            [clojure.java.jdbc :as jdbc]
            [hugsql.core :as hugsql]
            [clojure.edn :as edn]
            [clojure.string :as str]
            [schema.core :as s]))

(timbre/refer-timbre)

;; ----------------------------------------------------------------------
;; Auxiliary and utility functions

(hugsql/def-db-fns "planwise/sql/scenarios.sql")

(defn get-db
  [store]
  (get-in store [:db :spec]))

(defn- sum-investments
  [changeset]
  (apply +' (map :investment changeset)))

;; ----------------------------------------------------------------------
;; Service definition

(defn list-scenarios
  [store project-id]
  ;; TODO sort by order, compute optimal
  ;; TODO compute % coverage from initial scenario/project
  (db-list-scenarios (get-db store) {:project-id project-id}))

(defn get-scenario
  [store scenario-id]
  ;; TODO compute % coverage from initial scenario/projects
  (-> (db-find-scenario (get-db store) {:id scenario-id})
      (update :changeset edn/read-string)))

(defn create-initial-scenario
  [store project-id]
  ;; TODO schedule demand computation
  (db-create-scenario! (get-db store)
    {:name "Initial"
     :project-id project-id
     :investment 0
     :changeset "[]"}))

(defn create-scenario
  [store project-id {:keys [name changeset]}]
  ;; TODO schedule demand computation
  (s/validate model/ChangeSet changeset)
  (db-create-scenario! (get-db store)
    {:name name
     :project-id project-id
     :investment (sum-investments changeset)
     :changeset (pr-str changeset)}))

(defn update-scenario
  [store scenario-id {:keys [name changeset]}]
  ;; TODO schedule demand computation
  (s/validate model/ChangeSet changeset)
  (db-update-scenario! (get-db store)
    {:name name
     :id scenario-id
     :investment (sum-investments changeset)
     :changeset (pr-str changeset)}))

(defrecord ScenariosStore [db]
  boundary/Scenarios
  (list-scenarios [store project-id]
    (list-scenarios store project-id))
  (get-scenario [store scenario-id]
    (get-scenario store scenario-id))
  (create-initial-scenario [store project-id]
    (create-initial-scenario store project-id))
  (create-scenario [store project-id props]
    (create-scenario store project-id props))
  (update-scenario [store scenario-id props]
    (update-scenario store scenario-id props)))

(defmethod ig/init-key :planwise.component/scenarios
  [_ config]
  (map->ScenariosStore config))

(comment
  ;; REPL testing
  (def store (:planwise.component/scenarios integrant.repl.state/system))

  (list-scenarios store 1) ; project-id: 1
  (get-scenario store 2)) ; scenario-id 2
