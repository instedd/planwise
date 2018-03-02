(ns planwise.component.sites-datasets-test
  (:require [planwise.component.sites-datasets :as sites-datasets]
            [planwise.test-system :refer [test-system with-system]]
            [com.stuartsierra.component :as component]
            [clj-time.core :as time]
            [clojure.test :refer :all]))

(def owner-id 1)

(def fixture-user
  [[:users
    [{:id owner-id :email "jdoe@example.org"}]]
   [:sites_datasets
    []]
   [:sites_facilities
    []]])

(def fixture-listing-datasets
  [[:users
    [{:id owner-id :email "jdoe@example.org"}]]
   [:sites_datasets
    [{:id 1 :name "First" "\"owner-id\"" owner-id }
     {:id 2 :name "Bar" "\"owner-id\"" owner-id}]]
   [:sites_facilities
     []]])

(defn system
  ([]
   (system fixture-user))
  ([data]
   (into
    (test-system {:fixtures {:data data}})
    {:sites-datasets-store (component/using (sites-datasets/sites-datasets-store) [:db])})))

;; ----------------------------------------------------------------------
;; Testing dataset's creation

(deftest empty-list-of-dataset
  (with-system (system)
    (let [store (:sites-datasets-store system)
          datasets (sites-datasets/list-sites-datasets store owner-id)]
      (is (= (count datasets) 0)))))

(deftest list-of-datasets
  (with-system (system fixture-listing-datasets)
    (let [store (:sites-datasets-store system)
          datasets (sites-datasets/list-sites-datasets store owner-id)]
      (is (= (count datasets) 2)))))

(deftest create-dataset
  (with-system (system)
    (let [store (:sites-datasets-store system)
          dataset-id (:id (sites-datasets/create-sites-dataset store "Initial" owner-id))
          datasets (sites-datasets/list-sites-datasets store owner-id)
          version (sites-datasets/get-dataset-version store dataset-id)]
      (is (= (count datasets) 1))
      (is (= version 0)))))

;; ----------------------------------------------------------------------
;; Testing site's creation from csv-file

(deftest csv-to-correct-dataset
  (with-system (system)
    (let [store                    (:sites-datasets-store system)
          dataset1-id              (:id (sites-datasets/create-sites-dataset store "Initial" owner-id))
          dataset2-id              (:id (sites-datasets/create-sites-dataset store "Other" owner-id))
          facilities-dataset1      (sites-datasets/csv-to-facilities store dataset1-id "sites.csv")
          version-dataset1         (sites-datasets/get-dataset-version store dataset1-id)
          version-dataset2         (sites-datasets/get-dataset-version store dataset2-id)
          listed-sites-dataset1    (sites-datasets/find-sites-facilities store dataset1-id version-dataset1)
          listed-sites-dataset2    (sites-datasets/find-sites-facilities store dataset2-id version-dataset2)]
       (is (= (count listed-sites-dataset1) 4)
       (is (= (count listed-sites-dataset2) 0))))))

(deftest several-csv-to-dataset
 (with-system (system)
   (let [store                    (:sites-datasets-store system)
         dataset-id               (:id (sites-datasets/create-sites-dataset store "Initial" owner-id))
         sites                    (sites-datasets/csv-to-facilities store dataset-id "sites.csv")
         other-sites              (sites-datasets/csv-to-facilities store dataset-id "other-sites.csv")
         last-version-dataset     (sites-datasets/get-dataset-version store dataset-id)
         listed-sites             (sites-datasets/find-sites-facilities store dataset-id last-version-dataset)]
      (is (= (count listed-sites) 2))
      (is (= last-version-dataset 2)))))
