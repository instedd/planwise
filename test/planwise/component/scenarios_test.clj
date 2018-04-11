(ns planwise.component.scenarios-test
  (:require [clojure.test :refer :all]
            [clojure.java.io :as io]
            [planwise.component.scenarios :as scenarios]
            [planwise.boundary.projects2 :as projects2]
            [planwise.model.scenarios :as model]
            [planwise.test-system :as test-system]
            [planwise.util.collections :refer [find-by]]
            [clj-time.core :as time]
            [integrant.core :as ig]
            [clojure.spec.alpha :as s]
            [planwise.boundary.projects2 :as projects2]))

(def owner-id 10)
(def project-id 20)

(def fixture
  [[:users
    [{:id owner-id :email "jdoe@example.org"}]]
   [:projects2
    [{:id project-id :owner-id owner-id :name "" :config nil :dataset-id nil}]]
   [:scenarios
    []]])

(def initial-scenario-id 30)
(def sub-optimal-scenario-id 31)
(def optimal-scenario-id 32)
(def best-scenario-id 33)
(def other-scenario-id 34)

(def fixture-with-scenarios
  [[:users
    [{:id owner-id :email "jdoe@example.org"}]]
   [:projects2
    [{:id project-id :owner-id owner-id :name "" :config nil :dataset-id nil}]]
   [:scenarios
    [{:investment 0    :demand-coverage 100 :id initial-scenario-id     :label "initial" :name "Initial" :project-id project-id :changeset "[]"}
     {:investment 500  :demand-coverage 120 :id sub-optimal-scenario-id :label nil       :name "S1"      :project-id project-id :changeset "[]"}
     {:investment 500  :demand-coverage 150 :id best-scenario-id        :label nil       :name "S2"      :project-id project-id :changeset "[]"}
     {:investment 1000 :demand-coverage 150 :id optimal-scenario-id     :label nil       :name "S3"      :project-id project-id :changeset "[]"}
     {:investment 1000 :demand-coverage 120 :id other-scenario-id       :label nil       :name "S4"      :project-id project-id :changeset "[]"}]]])

(defn test-config
  ([]
   (test-config fixture))
  ([data]
   (test-system/config
    {:planwise.test/fixtures       {:fixtures data}
     :planwise.component/projects2 {:db (ig/ref :duct.database/sql)}
     :planwise.component/scenarios {:db (ig/ref :duct.database/sql)}})))

;; ----------------------------------------------------------------------
;; Testing scenario's creation

(deftest empty-list-of-scenarios
  (test-system/with-system (test-config)
    (let [store (:planwise.component/scenarios system)
          scenarios (scenarios/list-scenarios store project-id)]
      (is (= (count scenarios) 0)))))

(deftest initial-scenario-has-empty-changeset
  (test-system/with-system (test-config)
    (let [store       (:planwise.component/scenarios system)
          projects2   (:planwise.component/projects2 system)
          project     (projects2/get-project projects2 project-id)
          scenario-id (:id (scenarios/create-initial-scenario store project))
          scenario    (scenarios/get-scenario store scenario-id)]
      (is (= (:name scenario) "Initial"))
      (is (= (:project-id scenario) project-id))
      (is (= (:changeset scenario) [])))))

(deftest create-scenario-with-new-sites
  (test-system/with-system (test-config)
    (let [store       (:planwise.component/scenarios system)
          first-action {:action "create-site" :site-id "new.1" :investment 10000 :capacity 50}
          second-action {:action "create-site" :site-id "new.2" :investment 5000 :capacity 20}
          props       {:name "Foo" :changeset [first-action second-action]}
          scenario-id (:id (scenarios/create-scenario store project-id props))
          scenario    (scenarios/get-scenario store scenario-id)]
      (is (= (:name scenario) (:name props)))
      (is (= (:project-id scenario) project-id))
      (is (= (:changeset scenario) (:changeset props)))

      ;; computes sum of investments of actions
      (is (= (:investment scenario) 15000M)))))

(deftest list-scenarios-order
  (test-system/with-system (test-config fixture-with-scenarios)
    (let [store        (:planwise.component/scenarios system)
          _            (scenarios/db-update-scenarios-label! (scenarios/get-db store) {:project-id project-id})
          scenarios    (scenarios/list-scenarios store project-id)
          scenario-ids (map :id scenarios)]
      (is (= (take 3 scenario-ids) [initial-scenario-id best-scenario-id optimal-scenario-id]))
      (is (= (:label (find-by scenarios :id initial-scenario-id)) "initial"))
      (is (= (:label (find-by scenarios :id best-scenario-id)) "best"))
      (is (= (:label (find-by scenarios :id optimal-scenario-id)) "optimal")))))

(deftest valid-changeset
  (are [x] (s/valid? ::model/change-set x)
    []
    [{:action "create-site" :site-id "new.1" :investment 10000 :capacity 50}]))

(deftest invalid-changeset
  (are [x] (not (s/valid? ::model/change-set x))
    [{:action "unknown-action" :site-id "new.1" :investment 10000 :capacity 50}]
    [{:action "create-site" :site-id "new.1" :investment nil :capacity nil}]
    [{:action "create-site" :site-id "new.1" :investment "" :capacity ""}]
    [{:action "create-site" :site-id "new.1"}]))
