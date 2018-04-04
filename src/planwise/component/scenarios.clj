(ns planwise.component.scenarios
  (:require [planwise.boundary.scenarios :as boundary]
            [planwise.model.scenarios :as model]
            [integrant.core :as ig]
            [taoensso.timbre :as timbre]
            [clojure.java.jdbc :as jdbc]
            [hugsql.core :as hugsql]
            [clojure.edn :as edn]
            [clojure.string :as str]
            [clojure.spec.alpha :as s]))

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
                        :demand-coverage nil
                        :changeset "[]"
                        :label "initial"}))

(defn create-scenario
  [store project-id {:keys [name changeset]}]
  ;; TODO schedule demand computation
  (assert (s/valid? ::model/change-set changeset))
  (db-create-scenario! (get-db store)
                       {:name name
                        :project-id project-id
                        :investment (sum-investments changeset)
                        :demand-coverage nil
                        :changeset (pr-str changeset)
                        :label nil}))

(defn update-scenario
  [store scenario-id {:keys [name changeset]}]
  ;; TODO schedule demand computation
  ;; TODO fail if updating initial. initial scenario should be readonly
  (assert (s/valid? ::model/change-set changeset))
  (let [db (get-db store)
        project-id (:project-id (db-find-scenario db scenario-id))]
    (db-update-scenario! db
                         {:name name
                          :id scenario-id
                          :investment (sum-investments changeset)
                          :demand-coverage nil
                          :changeset (pr-str changeset)
                          :label nil})
    ;; Current label is removed so we need to search for the new optimal
    (db-update-scenarios-label! db {:project-id project-id})))

;; private function to update the label based on investments and demand-coverage
;; will label of all scenarios of the project
(defn update-scenario-demand-coverage
  [store scenario-id demand-coverage]
  (let [db         (get-db store)
        scenario   (-> (db-find-scenario db scenario-id)
                       (update :demand-coverage demand-coverage))
        project-id (:project-id scenario)]
    (db-update-scenario! db scenario)
    (db-update-scenarios-label! db {:project-id project-id})))

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
