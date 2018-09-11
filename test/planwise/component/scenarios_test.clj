(ns planwise.component.scenarios-test
  (:require [clojure.test :refer :all]
            [shrubbery.core :refer :all]
            [clojure.java.io :as io]
            [planwise.component.scenarios :as scenarios]
            [planwise.boundary.projects2 :as projects2]
            [planwise.boundary.jobrunner :as jobrunner]
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
    [{:id project-id :owner-id owner-id :name "" :config nil :provider-set-id nil}]]
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
    [{:id project-id :owner-id owner-id :name "" :config nil :provider-set-id nil}]]
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
    {:planwise.test/fixtures           {:fixtures data}
     :planwise.component/providers-set {:db (ig/ref :duct.database/sql)}
     :planwise.component/projects2     {:db (ig/ref :duct.database/sql)
                                        :providers-set (ig/ref :planwise.component/providers-set)}
     :planwise.component/scenarios {:db (ig/ref :duct.database/sql)
                                    :jobrunner (stub jobrunner/JobRunner
                                                     {:queue-job :enqueued})}})))

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
          scenario-id (scenarios/create-initial-scenario store project)
          scenario    (scenarios/get-scenario store scenario-id)]
      (is (= (:name scenario) "Initial"))
      (is (= (:project-id scenario) project-id))
      (is (= (:state scenario) "pending"))
      (is (= (:changeset scenario) [])))))

(deftest create-scenario-with-new-providers
  (test-system/with-system (test-config)
    (let [store       (:planwise.component/scenarios system)
          projects2   (:planwise.component/projects2 system)
          first-action {:action "create-provider" :id "new.1" :investment 10000 :capacity 50 :location {:lat 0 :lon 0}}
          second-action {:action "create-provider" :id "new.2" :investment 5000 :capacity 20 :location {:lat 0 :lon 0}}
          props       {:name "Foo" :changeset [first-action second-action]}
          project     (projects2/get-project projects2 project-id)
          scenario-id (:id (scenarios/create-scenario store project props))
          scenario    (scenarios/get-scenario store scenario-id)]
      (is (= (:name scenario) (:name props)))
      (is (= (:project-id scenario) project-id))
      (is (= (:state scenario) "pending"))
      (is (= (map #(dissoc % :id) (:changeset scenario)) (map #(dissoc % :id) (:changeset props))))

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
    [{:action "create-provider" :id "new.1" :investment 1000 :capacity 50 :location {:lat 0 :lon 0}}]
    [{:action "create-provider" :id "new.2" :investment 10000 :capacity 50 :location {:lat 0 :lon 0}}]))

(deftest invalid-changeset
  (are [x] (not (s/valid? ::model/change-set x))
    [{:action "unknown-action"  :id "new.1" :investment 10000 :capacity 50 :location {:lat 0 :lon 0}}]
    [{:action "create-provider" :id "new.1" :investment nil :capacity nil}]
    [{:action "create-provider" :id "new.1" :investment "" :capacity ""}]
    [{:action "create-provider" :id "new.1"}]))

(deftest initial-scenario-read-only
  (test-system/with-system (test-config)
    (let [store       (:planwise.component/scenarios system)
          projects2   (:planwise.component/projects2 system)
          project     (projects2/get-project projects2 project-id)
          scenario-id (scenarios/create-initial-scenario store project)
          scenario    (scenarios/get-scenario store scenario-id)
          new-scenario (assoc scenario
                              :changeset [{:action "create-provider" :id "new.1" :investment 10000 :capacity 50 :location {:lat 0 :lon 0}}]
                              :label "sub-optimal"
                              :investment 10000)]
      (do
        (is (thrown? java.lang.AssertionError (scenarios/update-scenario store project new-scenario)))
        (let [updated-scenario (scenarios/get-scenario store scenario-id)
              check-key (fn [key] (= (-> updated-scenario key) (-> scenario key)))]
          (is (map check-key [:id :changeset :investment :state])))))))
