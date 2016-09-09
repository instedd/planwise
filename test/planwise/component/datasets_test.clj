(ns planwise.component.datasets-test
  (:require [planwise.component.datasets :as datasets]
            [planwise.boundary.datasets :as datasets-protocol]
            [planwise.test-system :refer [test-system with-system]]
            [planwise.test-utils :refer [sample-polygon]]
            [com.stuartsierra.component :as component]
            [clj-time.core :as time]
            [clojure.test :refer :all]))


(def owner-id 1)
(def grantee-id 2)

(def fixture-data
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

(defn system
 ([]
  (system fixture-data))
 ([data]
  (into
   (test-system {:fixtures {:data data}})
   {:datasets (component/using (datasets/datasets-store) [:db])})))

(deftest dataset-owned-by
  (with-system (system)
    (let [dataset (datasets/find-dataset (:datasets system) 1)]
      (is (datasets-protocol/owned-by? dataset 1))
      (is (not (datasets-protocol/owned-by? dataset 2))))))

(deftest dataset-accessible-by
  (with-system (system)
    (let [store (:datasets system)]
      (is (datasets/accessible-by? store {:id 1 :owner-id 1} owner-id))
      (is (datasets/accessible-by? store {:id 2 :owner-id 1} owner-id))
      (is (datasets/accessible-by? store {:id 3 :owner-id 1} owner-id))
      (is (datasets/accessible-by? store {:id 1 :owner-id 1} grantee-id))
      (is (not (datasets/accessible-by? store {:id 2 :owner-id 1} grantee-id)))
      (is (not (datasets/accessible-by? store {:id 3 :owner-id 1} grantee-id))))))
