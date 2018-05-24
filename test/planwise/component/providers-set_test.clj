(ns planwise.component.providers-set-test
  (:require [clojure.test :refer :all]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [planwise.component.providers-set :as providers-set]
            [planwise.test-system :as test-system]
            [clj-time.core :as time]
            [integrant.core :as ig])
  (:import [org.postgis PGgeometry]))

(def owner-id 1)

(def fixture-user
  [[:users
    [{:id owner-id :email "jdoe@example.org"}]]
   [:providers-set
    []]
   [:sites2
    []]])

(def fixture-listing-providers-set
  [[:users
    [{:id owner-id :email "jdoe@example.org"}]]
   [:providers-set
    [{:id 1 :name "First" "owner-id" owner-id}
     {:id 2 :name "Bar" "owner-id" owner-id}]]
   [:sites2
    []]])

(defn point [input]
  (PGgeometry. (str input)))

(defn sample-polygon []
  (PGgeometry. (str "SRID=4326;MULTIPOLYGON(((0 0, 40 0, 40 -20, 0 -20, 0 0)))")))

(def fixture-filtering-sites-tags
  [[:users
    [{:id owner-id :email "jdoe@example.org"}]]
   [:providers-set
    [{:id 1 :name "First" "owner-id" owner-id "last-version" 2}
     {:id 2 :name "Bar" "owner-id" owner-id "last-version" 2}]]
   [:regions
    [{:id 1 :country "AAA" :name "Aaa" "the_geom" (sample-polygon)}]]
   [:projects2
    [{:id 1 "owner-id" owner-id :name "foo" :provider-set-id 1 :region-id 1}
     {:id 2 "owner-id" owner-id :name "foo2" :provider-set-id 2 :region-id 1}]]
   [:sites2
    [{:id 13 :lon 23.416667 :version 2 :the_geom (point "SRID=4326; POINT(23.416667 -19.983333)") "source-id" 1
      "processing-status" ":ok" "provider-set-id" 1 :tags "private laboratory radiography pharmacy"
      :type "hospital" :lat -19.983333 :capacity 50 :name "Nanyuki Cottage Hospital"}
     {:id 14 :lon 36.72609 :version 2 :the_geom (point "SRID=4326;POINT(36.72609 -1.336063)")
      "source-id" 2 "processing-status" ":ok" "provider-set-id" 1 :tags "private general-wards pediatric-ward 24hs-pharmacy  "
      :type "hospital" :lat -1.336063 :capacity 102 :name "Karen Hospital"}
     {:id 15 :lon 36.796071 :version 1 :the_geom (point "SRID=4326;POINT(36.796071 -1.293856)")
      "source-id" 3 "processing-status" ":ok" "provider-set-id" 1 :tags "private obstetrics gynecology-service "
      :type "hospital" :lat -1.293856 :capacity 356 :name "The Nairobi Women's Hospital"}
     {:id 16 :lon 36.796071 :version 1 :the_geom (point "SRID=4326;POINT(36.796071 -1.293856)")
      "source-id" 3 "processing-status" ":ok" "provider-set-id" 2 :tags "private general-medicine obstetrics gynecology-service "
      :type "hospital" :lat -1.293856 :capacity 356 :name "The Nairobi Women's Hospital"}
     {:id 17 :lon 36.80772 :version 2 :the_geom (point "SRID=4326;POINT(36.80772 -1.3017)")
      "source-id" 4 "processing-status" ":ok" "provider-set-id" 2 :tags "public surgical-services general-medicine"
      :type "hospital" :lat -1.3017 :capacity 1800 :name "Kenyatta National Hospital"}]]])


(defn test-config
  ([]
   (test-config fixture-user))
  ([data]
   (test-system/config
    {:planwise.test/fixtures       {:fixtures data}
     :planwise.component/providers-set {:db (ig/ref :duct.database/sql)}})))

;; ----------------------------------------------------------------------
;; Testing provider-set's creation

(deftest empty-list-of-provider-set
  (test-system/with-system (test-config)
    (let [store (:planwise.component/providers-set system)
          providers-set (providers-set/list-providers-set store owner-id)]
      (is (= (count providers-set) 0)))))

(deftest list-of-providers-set
  (test-system/with-system (test-config fixture-listing-providers-set)
    (let [store (:planwise.component/providers-set system)
          providers-set (providers-set/list-providers-set store owner-id)]
      (is (= (count providers-set) 2)))))

(deftest create-provider-set
  (test-system/with-system (test-config)
    (let [store (:planwise.component/providers-set system)
          provider-set-id (:id (providers-set/create-provider-set store "Initial" owner-id :none))
          providers-set (providers-set/list-providers-set store owner-id)
          version (:last-version (providers-set/get-provider-set store provider-set-id))]
      (is (= (count providers-set) 1))
      (is (= version 0)))))

;; ----------------------------------------------------------------------
;; Testing site's creation from csv-file

(deftest csv-to-correct-provider-set
  (test-system/with-system (test-config)
    (let [store                    (:planwise.component/providers-set system)
          provider-set1-id              (:id (providers-set/create-provider-set store "Initial" owner-id :none))
          provider-set2-id              (:id (providers-set/create-provider-set store "Other" owner-id :none))
          facilities-provider-set1      (providers-set/csv-to-sites store provider-set1-id (io/resource "sites.csv"))
          version-provider-set1         (:last-version (providers-set/get-provider-set store provider-set1-id))
          version-provider-set2         (:last-version (providers-set/get-provider-set store provider-set2-id))
          listed-sites-provider-set1    (providers-set/sites-by-version store provider-set1-id version-provider-set1)
          listed-sites-provider-set2    (providers-set/sites-by-version store provider-set2-id version-provider-set2)]
      (is (= (count listed-sites-provider-set1) 4)
          (is (= (count listed-sites-provider-set2) 0))))))

(defn- pd [v] (do (println v) v))

(deftest several-csv-to-provider-set
  (test-system/with-system (test-config)
    (let [store                    (:planwise.component/providers-set system)
          provider-set-id               (pd (:id (providers-set/create-provider-set store "Initial" owner-id :none)))
          sites                    (providers-set/csv-to-sites store provider-set-id (io/resource "sites.csv"))
          other-sites              (providers-set/csv-to-sites store provider-set-id (io/resource "other-sites.csv"))
          last-version-provider-set     (:last-version (providers-set/get-provider-set store provider-set-id))
          listed-sites             (providers-set/sites-by-version store provider-set-id last-version-provider-set)]
      (is (= (count listed-sites) 2))
      (is (= last-version-provider-set 2)))))

;; ----------------------------------------------------------------------
;; Testing site's tag filtering

(defn- validate-filter-count
  [store id tags number]
  (is (= (:filtered (providers-set/count-sites-filter-by-tags store id 1 tags)) number)))

(deftest filtering-sites
  (test-system/with-system (test-config fixture-filtering-sites-tags)
    (let [store                    (:planwise.component/providers-set system)
          providers-id1            (providers-set/sites-by-version store 1 2)
          number                   (count providers-id1)]
      (validate-filter-count store 1 [""] number)
      (validate-filter-count store 1 ["inexistent"] 0)
      (validate-filter-count store 1 ["private"] 2)
      (validate-filter-count store 2 ["private"] 0)
      (validate-filter-count store 2 ["-"] 0))))
