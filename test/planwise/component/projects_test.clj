(ns planwise.component.projects-test
  (:require [planwise.component.projects :as projects]
            [planwise.component.facilities :as facilities]
            [planwise.test-system :refer [test-system with-system]]
            [com.stuartsierra.component :as component]
            [clj-time.core :as time]
            [clojure.test :refer :all])
  (:import [org.postgis PGgeometry]))

(defn sample-polygon []
  (PGgeometry. (str "SRID=4326;MULTIPOLYGON(((0 0, 0 1, 1 1, 1 0, 0 0)))")))

(def fixture-data
  [[:users
    [{:id 1 :email "john@doe.com" :full_name "John Doe" :last_login nil :created_at (time/ago (time/minutes 5))}]]
   [:tokens []]
   [:regions
    [{:id 1 :country "kenya" :name "Kenya" :admin_level 2 :the_geom (sample-polygon) :preview_geom nil :total_population 1000}]]
   [:projects
    [{:id 1 :goal "" :region_id 1 :filters "" :stats "" :owner_id 1}]]])

(defn system []
  (into
   (test-system {:fixtures {:data fixture-data}})
   {:facilities (component/using (facilities/facilities-service {:config {}}) [])
    :projects (component/using (projects/projects-service) [:db :facilities])}))

(deftest region-population-is-retrieved-on-list-projects-for-user
  (with-system (system)
    (let [service (:projects system)
          listed-projects (projects/list-projects-for-user service 1)]
      (is (= 1000 (:region-population (first listed-projects)))))))
