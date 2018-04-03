(ns planwise.component.scenarios-test
  (:require [clojure.test :refer :all]
            [clojure.java.io :as io]
            [planwise.component.scenarios :as scenarios]
            [planwise.model.scenarios :as model]
            [planwise.test-system :as test-system]
            [clj-time.core :as time]
            [integrant.core :as ig]
            [schema.core :as s]))

(def owner-id 10)
(def project-id 20)

(def fixture
  [[:users
    [{:id owner-id :email "jdoe@example.org"}]]
   [:projects2
    [{:id project-id :owner-id owner-id :name "" :config nil :dataset-id nil}]]
   [:scenarios
    []]])

(defn test-config
  ([]
   (test-config fixture))
  ([data]
   (test-system/config
    {:planwise.test/fixtures       {:fixtures data}
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
          scenario-id (:id (scenarios/create-initial-scenario store project-id))
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

(deftest valid-changeset
  (let [t #(is (nil? (s/check model/ChangeSet %)))]
    (t [])
    (t [{:action "create-site" :site-id "new.1" :investment 10000 :capacity 50}])))

(deftest invalid-changeset
  (let [t #(is (some? (s/check model/ChangeSet %)))]
    (t [{:action "unknown-action" :site-id "new.1" :investment 10000 :capacity 50}])
    (t [{:action "create-site" :site-id "new.1" :investment nil :capacity nil}])
    (t [{:action "create-site" :site-id "new.1" :investment "" :capacity ""}])
    (t [{:action "create-site" :site-id "new.1"}])))
