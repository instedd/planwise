(ns planwise.component.projects-test
  (:require [planwise.component.projects :as projects]
            [planwise.component.facilities :as facilities]
            [planwise.test-system :refer [test-system with-system]]
            [com.stuartsierra.component :as component]
            [clj-time.core :as time]
            [clojure.test :refer :all])
  (:import [org.postgis PGgeometry]))

(defn sample-polygon []
  (PGgeometry. (str "SRID=4326;MULTIPOLYGON(((1 1, 1 2, 2 2, 2 1, 1 1)))")))

(def owner-id 1)
(def project-id 1)
(def shared-project-id 2)
(def grantee-user-id 2)
(def project-share-token "TOKEN1")

(def fixture-data
  [[:users
    [{:id owner-id :email "john@doe.com" :full_name "John Doe" :last_login nil :created_at (time/ago (time/minutes 5))}]]
   [:tokens []]
   [:datasets
    [{:id 1 :name "dataset1" :description "" :owner_id owner-id :collection_id 1 :import_mappings nil}]]
   [:regions
    [{:id 1 :country "kenya" :name "Kenya" :admin_level 2 :the_geom (sample-polygon) :preview_geom nil :total_population 1000 :max_population 127}]]
   [:projects
    [{:id project-id :goal "" :dataset_id 1 :region_id 1 :filters "" :stats "" :owner_id owner-id :share_token project-share-token}]]])

(def fixture-data-with-sharing
  (into []
    (-> (into {} fixture-data)
      (update :users into [{:id grantee-user-id :email "recipient@example.com" :full_name "Recipient" :last_login nil :created_at (time/ago (time/minutes 5))}])
      (update :projects into [{:id shared-project-id :goal "" :dataset_id 1 :region_id 1 :filters "" :stats "" :owner_id owner-id :share_token project-share-token}])
      (assoc  :project_shares [{:user_id grantee-user-id :project_id shared-project-id}]))))

(defn system
 ([]
  (system fixture-data))
 ([data]
  (into
   (test-system {:fixtures {:data data}})
   {:facilities (component/using (facilities/facilities-service {:config {}}) [])
    :projects (component/using (projects/projects-service) [:db :facilities])})))

(defn- count-project-shares [service project-id user-id]
  (->> service
    (projects/list-project-shares)
    (filter
      #(and
        (= user-id (:user-id %))
        (= project-id (:project-id %))))
    (count)))

(deftest region-information-is-retrieved-on-get-project
  (with-system (system)
    (let [service (:projects system)
          project (projects/get-project service 1)]
      (is (= 127 (:region-max-population project)))
      (is (= 1000 (:region-population project)))
      (is (pos? (:region-area-km2 project))))))

(deftest dataset-id-is-retrieved-on-get-project
  (with-system (system)
    (let [service (:projects system)
          project (projects/get-project service 1)]
      (is (= 1 (:dataset-id project))))))

(deftest region-population-is-retrieved-on-list-projects-for-user
  (with-system (system)
    (let [service (:projects system)
          listed-projects (projects/list-projects-for-user service 1)]
      (is (= 127 (:region-max-population (first listed-projects))))
      (is (= 1000 (:region-population (first listed-projects)))))))

(deftest shared-projects-are-retrieved-on-list-projects-for-user
  (with-system (system fixture-data-with-sharing)
    (let [service (:projects system)
          listed-projects (projects/list-projects-for-user service 2)]
      (is (= 1 (count listed-projects)))
      (is (= 2 (:id (first listed-projects)))))))

(deftest get-project-for-user-should-check-project-shares
  (with-system (system fixture-data-with-sharing)
    (let [service (:projects system)]
      (is (projects/get-project service shared-project-id owner-id))
      (is (projects/get-project service shared-project-id grantee-user-id))
      (is (projects/get-project service project-id owner-id))
      (is (nil? (projects/get-project service project-id grantee-user-id))))))

(deftest create-project-share
  (with-system (system fixture-data-with-sharing)
    (let [service (:projects system)
          project (projects/create-project-share service project-id project-share-token grantee-user-id)]
      (is project)
      (is (= project-id (:id project)))
      (is (= project (projects/get-project service project-id grantee-user-id)))
      (is (= 1 (count-project-shares service project-id grantee-user-id))))))

(deftest create-project-share-with-invalid-token
  (with-system (system fixture-data-with-sharing)
    (let [service (:projects system)
          project (projects/create-project-share service project-id "INVALIDTOKEN" grantee-user-id)]
      (is (nil? project))
      (is (nil? (projects/get-project service project-id grantee-user-id)))
      (is (zero? (count-project-shares service project-id grantee-user-id))))))

(deftest create-project-share-on-already-shared-project
  (with-system (system fixture-data-with-sharing)
    (let [service (:projects system)
          project (projects/create-project-share service shared-project-id project-share-token grantee-user-id)]
      (is project)
      (is (= shared-project-id (:id project)))
      (is (= project (projects/get-project service shared-project-id grantee-user-id)))
      (is (= 1 (count-project-shares service shared-project-id grantee-user-id))))))
