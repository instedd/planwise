(ns planwise.component.regions-test
  (:require [planwise.component.regions :as regions]
            [planwise.test-system :refer [test-system with-system]]
            [com.stuartsierra.component :as component]
            [clojure.test :refer :all])
  (:import [org.postgis PGgeometry]))

(defn sample-polygon []
  (PGgeometry. (str "SRID=4326;MULTIPOLYGON(((0 0, 0 1, 1 1, 1 0, 0 0)))")))

(def fixture-data
  [[:regions
    [{:id 1 :country "kenya" :name "Kenya" :admin_level 2 :the_geom (sample-polygon) :preview_geom nil :total_population 1000}]]])

#_(defn system []
  (into
   (test-system {:fixtures {:data fixture-data}})
   {:regions (component/using (regions/regions-service) [:db])}))

#_(deftest total-population-is-retrieved-with-list-regions
  (with-system (system)
    (let [service (:regions system)
          [kenya & rest] (regions/list-regions service)]
     (is (= 1000 (:total-population kenya))))))

#_(deftest total-population-is-retrieved-with-list-regions-with-preview
  (with-system (system)
    (let [service (:regions system)
          [kenya & rest] (regions/list-regions-with-preview service [1])]
      (is (= 1000 (:total-population kenya))))))

#_(deftest total-population-is-retrieved-with-list-regions-with-geo
  (with-system (system)
    (let [service (:regions system)
          [kenya & rest] (regions/list-regions-with-geo service [1] 0.5)]
      (is (= 1000 (:total-population kenya))))))
