(ns planwise.component.projects-test
  (:require [clojure.test :refer :all]
            [planwise.boundary.projects :as projects]
            [planwise.boundary.facilities :as facilities]
            [planwise.model.projects :as model]
            [planwise.test-system :as test-system]
            [planwise.test-utils :refer [sample-polygon]]
            [clj-time.jdbc]
            [clj-time.core :as time]
            [integrant.core :as ig]))

(def owner-id            1)
(def project-id          1)
(def shared-project-id   2)
(def grantee-user-id     2)
(def project-share-token "TOKEN1")

(def fixture-data
  [[:users
    [{:id owner-id :email "john@doe.com" :full_name "John Doe" :last_login nil :created_at (time/ago (time/minutes 5))}]]
   [:tokens []]
   [:datasets
    [{:id 1 :name "dataset1" :description "" :owner_id owner-id :collection_id 1 :import_mappings nil}]]
   [:regions
    [{:id 1 :country "kenya" :name "Kenya" :admin_level 2 :the_geom (sample-polygon) :preview_geom nil :total_population 1000 :max_population 127 :raster_pixel_area 950}]]
   [:projects
    [{:id project-id :goal "" :dataset_id 1 :region_id 1 :filters "" :stats "" :owner_id owner-id :share_token project-share-token}]]])

(def fixture-data-with-sharing
  (into []
        (-> (into {} fixture-data)
            (update :users into [{:id grantee-user-id :email "recipient@example.com" :full_name "Recipient" :last_login nil :created_at (time/ago (time/minutes 5))}])
            (update :projects into [{:id shared-project-id :goal "" :dataset_id 1 :region_id 1 :filters "" :stats "" :owner_id owner-id :share_token project-share-token}])
            (assoc  :project_shares [{:user_id grantee-user-id :project_id shared-project-id}]))))

(defn test-config
  ([]
   (test-config fixture-data))
  ([data]
   (test-system/config
    {:planwise.test/fixtures        {:fixtures data}
     :planwise.component/facilities {:db (ig/ref :duct.database/sql)}
     :planwise.component/projects   {:db (ig/ref :duct.database/sql)
                                     :facilities (ig/ref :planwise.component/facilities)}})))

(defn- count-project-shares [service project-id user-id]
  (->> (projects/list-project-shares service project-id)
       (filter (comp #{user-id} :user-id))
       (count)))

(deftest region-information-is-retrieved-on-get-project
  (test-system/with-system (test-config)
    (let [service (:planwise.component/projects system)
          project (projects/get-project service 1)]
      (is (= 127 (:region-max-population project)))
      (is (= 950 (:region-raster-pixel-area project)))
      (is (= 1000 (:region-population project)))
      (is (pos? (:region-area-km2 project))))))

(deftest dataset-id-is-retrieved-on-get-project
  (test-system/with-system (test-config)
    (let [service (:planwise.component/projects system)
          project (projects/get-project service 1)]
      (is (= 1 (:dataset-id project))))))

(deftest region-population-is-retrieved-on-list-projects-for-user
  (test-system/with-system (test-config)
    (let [service (:planwise.component/projects system)
          listed-projects (projects/list-projects-for-user service 1)]
      (is (= 127 (:region-max-population (first listed-projects))))
      (is (= 1000 (:region-population (first listed-projects)))))))

(deftest shared-projects-are-retrieved-on-list-projects-for-user
  (test-system/with-system (test-config fixture-data-with-sharing)
    (let [service (:planwise.component/projects system)
          listed-projects (projects/list-projects-for-user service 2)]
      (is (= 1 (count listed-projects)))
      (is (= 2 (:id (first listed-projects))))
      (is (nil? (:share-token (first listed-projects)))))))

(deftest get-project-for-user-should-check-project-shares
  (test-system/with-system (test-config fixture-data-with-sharing)
    (let [service (:planwise.component/projects system)]
      (is (projects/get-project service shared-project-id owner-id))
      (is (projects/get-project service shared-project-id grantee-user-id))
      (is (projects/get-project service project-id owner-id))
      (is (nil? (projects/get-project service project-id grantee-user-id))))))

(deftest get-project-for-user-return-view-for-user
  (test-system/with-system (test-config fixture-data-with-sharing)
    (let [service (:planwise.component/projects system)
          project-for-owner (projects/get-project service shared-project-id owner-id)
          project-for-grantee (projects/get-project service shared-project-id grantee-user-id)]
      (is (= project-share-token (:share-token project-for-owner)))
      (is (nil? (:share-token project-for-grantee)))
      (is (not (:read-only project-for-owner)))
      (is (:read-only project-for-grantee)))))

(deftest create-project-share
  (test-system/with-system (test-config fixture-data-with-sharing)
    (let [service (:planwise.component/projects system)
          project (projects/create-project-share service project-id project-share-token grantee-user-id)]
      (is project)
      (is (= project-id (:id project)))
      (is (nil? (:share-token project)))
      (is (:read-only project))
      (is (= project-id (:id (projects/get-project service project-id grantee-user-id))))
      (is (= 1 (count-project-shares service project-id grantee-user-id))))))

(deftest create-project-share-with-invalid-token
  (test-system/with-system (test-config fixture-data-with-sharing)
    (let [service (:planwise.component/projects system)
          project (projects/create-project-share service project-id "INVALIDTOKEN" grantee-user-id)]
      (is (nil? project))
      (is (nil? (projects/get-project service project-id grantee-user-id)))
      (is (zero? (count-project-shares service project-id grantee-user-id))))))

(deftest create-project-share-on-already-shared-project
  (test-system/with-system (test-config fixture-data-with-sharing)
    (let [service (:planwise.component/projects system)
          project (projects/create-project-share service shared-project-id project-share-token grantee-user-id)]
      (is project)
      (is (= shared-project-id (:id project)))
      (is (= shared-project-id (:id (projects/get-project service shared-project-id grantee-user-id))))
      (is (= 1 (count-project-shares service shared-project-id grantee-user-id))))))

(deftest reset-share-token
  (test-system/with-system (test-config)
    (let [service (:planwise.component/projects system)
          new-token (projects/reset-share-token service project-id)
          updated-project (projects/get-project service project-id)]
      (is (not= new-token project-share-token))
      (is (= new-token (:share-token updated-project))))))

(deftest share-project-url
  (let [expected "http://planwise.instedd.org/projects/1/access/TOKEN"
        project {:id 1 :share-token "TOKEN"}]
    (are [host] (= expected (model/share-project-url host project))
      "http://planwise.instedd.org"
      "http://planwise.instedd.org/"
      "http://planwise.instedd.org/some/path")))
