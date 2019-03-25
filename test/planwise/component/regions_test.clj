(ns planwise.component.regions-test
  (:require [clojure.test :refer :all]
            [planwise.boundary.regions :as regions]
            [planwise.test-system :as test-system]
            [integrant.core :as ig])
  (:import [org.postgis PGgeometry]))

(defn sample-polygon []
  (PGgeometry. (str "SRID=4326;MULTIPOLYGON(((0 0, 0 1, 1 1, 1 0, 0 0)))")))

(def fixture-data
  [[:regions
    [{:id 1 :country "kenya" :name "Kenya" :admin_level 2 :the_geom (sample-polygon) :preview_geom nil :total_population 1000}]]])

(defn test-config
  []
  (test-system/config
   {:planwise.test/fixtures     {:fixtures fixture-data}
    :planwise.component/regions {:db (ig/ref :duct.database/sql)}}))

(deftest total-population-is-retrieved-with-list-regions
  (test-system/with-system (test-config)
    (let [service (:planwise.component/regions system)
          [kenya & rest] (regions/list-regions service)]
      (is (= 1000 (:total-population kenya))))))

(deftest total-population-is-retrieved-with-list-regions-with-preview
  (test-system/with-system (test-config)
    (let [service (:planwise.component/regions system)
          [kenya & rest] (regions/list-regions-with-preview service [1])]
      (is (= 1000 (:total-population kenya))))))

(deftest total-population-is-retrieved-with-list-regions-with-geo
  (test-system/with-system (test-config)
    (let [service (:planwise.component/regions system)
          [kenya & rest] (regions/list-regions-with-geo service [1] 0.5)]
      (is (= 1000 (:total-population kenya))))))

(deftest enum-regions-intersecting-envelope-test
  (test-system/with-system (test-config)
    (let [service (:planwise.component/regions system)]
      (is (empty? (regions/enum-regions-intersecting-envelope service {:min-lat 2 :max-lat 3 :min-lon 1 :max-lon 2})))
      (is (= [1] (regions/enum-regions-intersecting-envelope service {:min-lat 0 :max-lat 2 :min-lon 0 :max-lon 2})))
      (is (= [1] (regions/enum-regions-intersecting-envelope service {:min-lat 0.5 :max-lat 2 :min-lon 0.5 :max-lon 2}))))))
