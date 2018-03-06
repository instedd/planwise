(ns planwise.component.datasets-test
  (:require [clojure.test :refer :all]
            [planwise.boundary.datasets :as datasets]
            [planwise.model.datasets :as model]
            [planwise.test-system :as test-system]
            [planwise.test-utils :refer [sample-polygon]]
            [clj-time.core :as time]
            [integrant.core :as ig]))

(def owner-id   1)
(def grantee-id 2)

(defn fixture-data
  []
  [[:users
    [{:id owner-id   :email "john@doe.com" :full_name "John Doe" :last_login nil :created_at (time/ago (time/minutes 5))}
     {:id grantee-id :email "jane@doe.com" :full_name "Jane Doe" :last_login nil :created_at (time/ago (time/minutes 5))}]]
   [:tokens []]
   [:datasets
    [{:id 1 :name "dataset1" :description "" :owner_id owner-id :collection_id 1 :import_mappings nil}
     {:id 2 :name "dataset2" :description "" :owner_id owner-id :collection_id 2 :import_mappings nil}
     {:id 3 :name "dataset3" :description "" :owner_id owner-id :collection_id 3 :import_mappings nil}]]
   [:regions
    [{:id 1 :country "kenya" :name "Kenya" :admin_level 2 :the_geom (sample-polygon) :preview_geom nil :total_population 1000 :max_population 127}]]
   [:projects
    [{:id 1 :goal "" :dataset_id 1 :region_id 1 :filters "" :stats "" :owner_id owner-id}
     {:id 2 :goal "" :dataset_id 1 :region_id 1 :filters "" :stats "" :owner_id owner-id}]]
   [:project_shares
    [{:user_id grantee-id :project_id 1}]]])

(defn test-config
  []
  (test-system/config
   {:planwise.test/fixtures      {:fixtures (fixture-data)}
    :planwise.component/datasets {:db (ig/ref :duct.database/sql)}}))

(deftest dataset-owned-by
  (test-system/with-system (test-config)
    (let [store (:planwise.component/datasets system)
          dataset (datasets/find-dataset store 1)]
      (is (model/owned-by? dataset 1))
      (is (not (model/owned-by? dataset 2))))))

(deftest dataset-accessible-by
  (test-system/with-system (test-config)
    (let [store (:planwise.component/datasets system)]
      (is (datasets/accessible-by? store {:id 1 :owner-id 1} owner-id))
      (is (datasets/accessible-by? store {:id 2 :owner-id 1} owner-id))
      (is (datasets/accessible-by? store {:id 3 :owner-id 1} owner-id))
      (is (datasets/accessible-by? store {:id 1 :owner-id 1} grantee-id))
      (is (not (datasets/accessible-by? store {:id 2 :owner-id 1} grantee-id)))
      (is (not (datasets/accessible-by? store {:id 3 :owner-id 1} grantee-id))))))
