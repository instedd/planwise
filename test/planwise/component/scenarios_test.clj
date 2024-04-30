(ns planwise.component.scenarios-test
  (:require [clojure.test :refer :all]
            [shrubbery.core :refer :all]
            [clojure.java.io :as io]
            [planwise.component.scenarios :as scenarios]
            [planwise.boundary.projects2 :as projects2]
            [planwise.boundary.jobrunner :as jobrunner]
            [planwise.model.scenarios :as model]
            [planwise.test-system :as test-system]
            [planwise.test-utils :as utils]
            [planwise.util.collections :refer [find-by csv-data->maps]]
            [clj-time.core :as time]
            [integrant.core :as ig]
            [clojure.data.csv :as csv]
            [clojure.spec.alpha :as s])
  (:import [org.postgis PGgeometry]))

(def owner-id 10)
(def project-id 20)

(def fixture
  [[:users
    [{:id owner-id :email "jdoe@example.org"}]]
   [:projects2
    [{:id project-id :owner-id owner-id :name "" :config nil :provider-set-id nil}]]
   [:scenarios
    []]])

(def fixture-action-type
  [[:users
    [{:id owner-id :email "jdoe@example.org"}]]
   [:projects2
    [{:id project-id :owner-id owner-id :name "" :config "{:analysis-type \"action\"}" :provider-set-id nil}]]
   [:scenarios
    []]])

(def initial-scenario-id 30)
(def sub-optimal-scenario-id 31)
(def optimal-scenario-id 32)
(def best-scenario-id 33)
(def other-scenario-id 34)

(def provider-set-id 40)
(def region-id 50)
(def provider-1 60)
(def provider-2 61)

(def fixture-with-scenarios
  [[:users
    [{:id owner-id :email "jdoe@example.org"}]]
   [:regions
    [{:id region-id :country "Testiland" :name "Testiland" :the_geom (utils/sample-polygon :large)}]]
   [:providers_set
    [{:id provider-set-id :name "Joe's providers" :owner-id owner-id :last-version 1}]]
   [:providers
    [{:id   provider-1 :source-id 1   :version 1   :provider-set-id provider-set-id
      :name "Site A"   :lat       0.0 :lon     0.0 :the_geom        (utils/make-point 0.0 0.0) :capacity 10 :tags "tag1"}
     {:id   provider-2 :source-id 2   :version 1   :provider-set-id provider-set-id
      :name "Site B"   :lat       1.0 :lon     1.0 :the_geom        (utils/make-point 1.0 1.0) :capacity 20 :tags "tag2"}]]
   [:projects2
    [{:id              project-id      :owner-id             owner-id :name "" :config nil
      :region-id       region-id
      :provider-set-id provider-set-id :provider-set-version 1}]]
   [:scenarios
    [{:effort 0    :demand-coverage 100 :id initial-scenario-id     :label "initial" :name "Initial" :project-id project-id :changeset "[]"}
     {:effort 500  :demand-coverage 120 :id sub-optimal-scenario-id :label nil       :name "S1"      :project-id project-id :changeset "[]"}
     {:effort 500  :demand-coverage 150 :id best-scenario-id        :label nil       :name "S2"      :project-id project-id :changeset "[]"}
     {:effort 1000 :demand-coverage 150 :id optimal-scenario-id     :label nil       :name "S3"      :project-id project-id :changeset "[]"}
     {:id              other-scenario-id
      :project-id      project-id
      :name            "S4"
      :effort          1000
      :demand-coverage 120
      :label           nil
      :changeset       (pr-str [{:action "create-provider" :id "new-0" :capacity 30 :location {:lat 0.5 :long 0.5}}
                                {:action "increase-provider" :id 61 :capacity 5}])
      :providers-data  (pr-str [{:id                 60
                                 :capacity           10
                                 :required-capacity  10
                                 :used-capacity      10
                                 :satisfied-demand   100
                                 :unsatisfied-demand 0}
                                {:id                 61
                                 :capacity           25
                                 :required-capacity  30
                                 :used-capacity      25
                                 :satisfied-demand   250
                                 :unsatisfied-demand 50}
                                {:id                 "new-0"
                                 :capacity           30
                                 :required-capacity  15
                                 :used-capacity      15
                                 :satisfied-demand   150
                                 :unsatisfied-demand 0}])}]]])

(defn test-config
  ([]
   (test-config fixture))
  ([data]
   (test-system/config
    {:planwise.test/fixtures           {:fixtures data}
     :planwise.component/providers-set {:db (ig/ref :duct.database/sql)}
     :planwise.component/projects2     {:db            (ig/ref :duct.database/sql)
                                        :providers-set (ig/ref :planwise.component/providers-set)}
     :planwise.component/scenarios     {:db            (ig/ref :duct.database/sql)
                                        :providers-set (ig/ref :planwise.component/providers-set)
                                        :jobrunner     (stub jobrunner/JobRunner
                                                             {:queue-job :enqueued})}})))

;; ----------------------------------------------------------------------
;; Testing scenario's creation

(deftest empty-list-of-scenarios
  (test-system/with-system (test-config)
    (let [store     (:planwise.component/scenarios system)
          projects2 (:planwise.component/projects2 system)
          project   (projects2/get-project projects2 project-id)
          scenarios (scenarios/list-scenarios store project)]
      (is (= (count scenarios) 0)))))

(deftest initial-scenario-has-empty-changeset
  (test-system/with-system (test-config)
    (let [store       (:planwise.component/scenarios system)
          projects2   (:planwise.component/projects2 system)
          project     (projects2/get-project projects2 project-id)
          scenario-id (scenarios/create-initial-scenario store project)
          scenario    (scenarios/get-scenario store scenario-id)]
      (is (= (get-in project [:config :analysis-type]) "budget"))
      (is (= (:name scenario) "Initial"))
      (is (= (:project-id scenario) project-id))
      (is (= (:state scenario) "pending"))
      (is (= (:changeset scenario) [])))))

(deftest create-scenario-with-new-providers
  (test-system/with-system (test-config)
    (let [store       (:planwise.component/scenarios system)
          projects2   (:planwise.component/projects2 system)
          first-action {:action "create-provider" :name "New provider 0" :id "new.1" :investment 10000 :capacity 50 :location {:lat 0 :lon 0}}
          second-action {:action "create-provider" :name "New provider 1" :id "new.2" :investment 5000 :capacity 20 :location {:lat 0 :lon 0}}
          props       {:name "Foo" :changeset [first-action second-action]}
          project     (projects2/get-project projects2 project-id)
          scenario-id (:id (scenarios/create-scenario store project props))
          scenario    (scenarios/get-scenario store scenario-id)]
      (is (= (:name scenario) (:name props)))
      (is (= (:project-id scenario) project-id))
      (is (= (:state scenario) "pending"))
      (is (= (map #(dissoc % :id) (:changeset scenario)) (map #(dissoc % :id) (:changeset props))))

      ;; computes sum of investments of actions
      (is (= (:effort scenario) 15000M)))))

(deftest create-action-scenario-with-new-providers
  (test-system/with-system (test-config fixture-action-type)
    (let [store       (:planwise.component/scenarios system)
          projects2   (:planwise.component/projects2 system)
          first-action {:action "create-provider" :name "New provider 0" :id "new.1" :investment 10000 :capacity 50 :location {:lat 0 :lon 0}}
          second-action {:action "create-provider" :name "New provider 1" :id "new.2" :investment 5000 :capacity 20 :location {:lat 0 :lon 0}}
          third-action {:action "create-provider" :name "New provider 2" :id "new.3" :investment 2000 :capacity 10 :location {:lat 0 :lon 0}}
          props       {:name "Foo" :changeset [first-action second-action third-action]}
          project     (projects2/get-project projects2 project-id)
          scenario-id (:id (scenarios/create-scenario store project props))
          scenario    (scenarios/get-scenario store scenario-id)]
      (is (= (get-in project [:config :analysis-type]) "action"))
      (is (= (:name scenario) (:name props)))
      (is (= (:project-id scenario) project-id))
      (is (= (:state scenario) "pending"))
      (is (= (map #(dissoc % :id) (:changeset scenario)) (map #(dissoc % :id) (:changeset props))))

      ;; count number of actions
      (is (= (:effort scenario) 3M)))))

(deftest list-scenarios-order
  (test-system/with-system (test-config fixture-with-scenarios)
    (let [store        (:planwise.component/scenarios system)
          _            (scenarios/db-update-scenarios-label! (scenarios/get-db store) {:project-id project-id})
          projects2    (:planwise.component/projects2 system)
          project      (projects2/get-project projects2 project-id)
          scenarios    (scenarios/list-scenarios store project)
          scenario-ids (map :id scenarios)]
      (is (= (take 3 scenario-ids) [initial-scenario-id best-scenario-id optimal-scenario-id]))
      (is (= (:label (find-by scenarios :id initial-scenario-id)) "initial"))
      (is (= (:label (find-by scenarios :id best-scenario-id)) "best"))
      (is (= (:label (find-by scenarios :id optimal-scenario-id)) "optimal")))))

(deftest valid-changeset
  (are [x] (s/valid? ::model/change-set x)
    []
    [{:action "create-provider" :name "New provider 1" :id "new.1" :investment 1000 :capacity 50 :location {:lat 0 :lon 0}}]
    [{:action "create-provider" :name "New provider 2" :id "new.2" :investment 10000 :capacity 50 :location {:lat 0 :lon 0}}]
    [{:action "create-provider" :name "New provider 3" :id "new.3" :investment 10000 :capacity 50 :location {:lat 0 :lon 0}}
     {:action "upgrade-provider" :id 1 :investment 10000 :capacity 50}]))

(deftest invalid-changeset
  (are [x] (not (s/valid? ::model/change-set x))
    [{:action "unknown-action"  :id "new.1" :investment 10000 :capacity 50 :location {:lat 0 :lon 0}}]
    [{:action "create-provider" :id "new.1" :investment nil :capacity nil}]
    [{:action "create-provider" :id "new.1" :investment "" :capacity ""}]
    [{:action "create-provider" :id "new.1"}]
    [{:action "create-provider" :id "new.1" :investment 1000 :capacity 50 :location {:lat 0 :lon 0}} {:action "upgrade-provider" :id "new.2"}]
    [{:action "increase-provider" :location {:lat 0 :lon 0}}]))

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
                              :effort 10000)]
      (do
        (is (thrown? java.lang.AssertionError (scenarios/update-scenario store project new-scenario)))
        (let [updated-scenario (scenarios/get-scenario store scenario-id)
              check-key (fn [key] (= (-> updated-scenario key) (-> scenario key)))]
          (is (map check-key [:id :changeset :effort :state])))))))


(deftest delete-current-scenario
  (test-system/with-system (test-config fixture-with-scenarios)
    (let [store       (:planwise.component/scenarios system)
          projects2   (:planwise.component/projects2 system)
          initial-scenario (scenarios/get-scenario store initial-scenario-id)
          scenario    (scenarios/get-scenario store other-scenario-id)]
      (is (some? scenario))
      (is (some? initial-scenario))
      (let [_        (scenarios/delete-scenario store other-scenario-id)
            _        (scenarios/delete-scenario store initial-scenario-id)
            scenario (scenarios/get-scenario store other-scenario-id)
            initial-scenario (scenarios/get-scenario store initial-scenario-id)]
        (is (nil? scenario))
        (is (map? initial-scenario))))))

(deftest export-providers-data
  (test-system/with-system (test-config fixture-with-scenarios)
    (let [store     (:planwise.component/scenarios system)
          projects2 (:planwise.component/projects2 system)
          project   (projects2/get-project projects2 project-id)
          scenario  (scenarios/get-scenario store other-scenario-id)
          csv       (scenarios/export-providers-data store project scenario)
          data      (csv-data->maps (csv/read-csv csv))]
      (is (= (set (keys (first data)))
             #{:id :type :name :lat :lon :tags :capacity :required-capacity :used-capacity :satisfied-demand :unsatisfied-demand}))
      (is (= 3 (count data)))
      (letfn [(find-and-select [id] (-> data (find-by :id id) (select-keys [:id :capacity])))]
        (is (= (find-and-select "60") {:id "60" :capacity "10"}))
        (is (= (find-and-select "61") {:id "61" :capacity "25"}))
        (is (= (find-and-select "new-0") {:id "new-0" :capacity "30"}))))))
