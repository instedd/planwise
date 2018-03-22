(ns planwise.component.datasets2-test
  (:require [clojure.test :refer :all]
            [clojure.java.io :as io]
            [planwise.component.datasets2 :as datasets2]
            [planwise.test-system :as test-system]
            [clj-time.core :as time]
            [integrant.core :as ig]))

(def owner-id 1)

(def fixture-user
  [[:users
    [{:id owner-id :email "jdoe@example.org"}]]
   [:datasets2
    []]
   [:sites2
    []]])

(def fixture-listing-datasets
  [[:users
    [{:id owner-id :email "jdoe@example.org"}]]
   [:datasets2
    [{:id 1 :name "First" "owner-id" owner-id }
     {:id 2 :name "Bar" "owner-id" owner-id}]]
   [:sites2
     []]])

(defn test-config
  ([]
   (test-config fixture-user))
  ([data]
   (test-system/config
    {:planwise.test/fixtures       {:fixtures data}
     :planwise.component/datasets2 {:db (ig/ref :duct.database/sql)}})))

;; ----------------------------------------------------------------------
;; Testing dataset's creation

(deftest empty-list-of-dataset
  (test-system/with-system (test-config)
    (let [store (:planwise.component/datasets2 system)
          datasets (datasets2/list-datasets store owner-id)]
      (is (= (count datasets) 0)))))

(deftest list-of-datasets
  (test-system/with-system (test-config fixture-listing-datasets)
    (let [store (:planwise.component/datasets2 system)
          datasets (datasets2/list-datasets store owner-id)]
      (is (= (count datasets) 2)))))

(deftest create-dataset
  (test-system/with-system (test-config)
    (let [store (:planwise.component/datasets2 system)
          dataset-id (:id (datasets2/create-dataset store "Initial" owner-id :none))
          datasets (datasets2/list-datasets store owner-id)
          version (:last-version (datasets2/get-dataset store dataset-id))]
      (is (= (count datasets) 1))
      (is (= version 0)))))

;; ----------------------------------------------------------------------
;; Testing site's creation from csv-file

(deftest csv-to-correct-dataset
  (test-system/with-system (test-config)
    (let [store                    (:planwise.component/datasets2 system)
          dataset1-id              (:id (datasets2/create-dataset store "Initial" owner-id :none))
          dataset2-id              (:id (datasets2/create-dataset store "Other" owner-id :none))
          facilities-dataset1      (datasets2/csv-to-sites store dataset1-id (io/resource "sites.csv"))
          version-dataset1         (:last-version (datasets2/get-dataset store dataset1-id))
          version-dataset2         (:last-version (datasets2/get-dataset store dataset2-id))
          listed-sites-dataset1    (datasets2/sites-by-version store dataset1-id version-dataset1)
          listed-sites-dataset2    (datasets2/sites-by-version store dataset2-id version-dataset2)]
       (is (= (count listed-sites-dataset1) 4)
       (is (= (count listed-sites-dataset2) 0))))))

(defn- pd [v] (do (println v) v))

(deftest several-csv-to-dataset
 (test-system/with-system (test-config)
   (let [store                    (:planwise.component/datasets2 system)
         dataset-id               (pd (:id (datasets2/create-dataset store "Initial" owner-id :none)))
         sites                    (datasets2/csv-to-sites store dataset-id (io/resource "sites.csv"))
         other-sites              (datasets2/csv-to-sites store dataset-id (io/resource "other-sites.csv"))
         last-version-dataset     (:last-version (datasets2/get-dataset store dataset-id))
         listed-sites             (datasets2/sites-by-version store dataset-id last-version-dataset)]
      (is (= (count listed-sites) 2))
      (is (= last-version-dataset 2)))))
