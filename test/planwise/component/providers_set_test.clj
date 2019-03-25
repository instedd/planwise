(ns planwise.component.providers-set-test
  (:require [clojure.test :refer :all]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [planwise.component.providers-set :as providers-set]
            [planwise.boundary.projects2 :as projects2]
            [planwise.test-system :as test-system]
            [clj-time.core :as time]
            [integrant.core :as ig])
  (:import [org.postgis PGgeometry]))

(def owner-id 1)

(def fixture-user
  [[:users
    [{:id owner-id :email "jdoe@example.org"}]]
   [:providers_set
    []]
   [:providers
    []]])

(def fixture-listing-providers-set
  [[:users
    [{:id owner-id :email "jdoe@example.org"}]]
   [:providers_set
    [{:id 1 :name "First" "owner-id" owner-id}
     {:id 2 :name "Bar" "owner-id" owner-id}]]
   [:providers
    []]])

(defn point [input]
  (PGgeometry. (str input)))

(defn sample-multipolygon []
  (PGgeometry. (str "SRID=4326;MULTIPOLYGON(((0 0, 40 0, 40 -20, 0 -20, 0 0)))")))

(def sample-polygon
  (PGgeometry. "SRID=4326;POLYGON((39.5687172638333 -3.5453833334101,39.5678576624725 -3.55420830717788,39.565301077619 -3.56269520969253,39.5611457123616 -3.57051788719156,39.555551221239 -3.57737570278502,39.5487325801022 -3.58300509215371,39.540951827481 -3.58718969562751,39.5325079943601 -3.58976867673065,39.5237256093713 -3.59064290688014,39.5149422216335 -3.58977877794039,39.5064954216325 -3.58720949561873,39.4987098600791 -3.58303380370404,39.4918847648304 -3.57741218813816,39.4862824366768 -3.57056070701659,39.4821181668736 -3.56274268406344,39.4795519642184 -3.55425858536699,39.4786824094641 -3.54543446901629,39.4795428726616 -3.53660945205977,39.4821002378828 -3.52812267682831,39.4862561832347 -3.52030027770673,39.4918509658648 -3.51344284920462,39.4986695655423 -3.50781389671009,39.5064499500491 -3.5036297133831,39.5148931444426 -3.50105107173583,39.5236747173314 -3.5001770486721,39.5324572432316 -3.50104122080053,39.5409032628647 -3.5036103758387,39.5486882443236 -3.50778578937558,39.5555130480657 -3.51340701784988,39.5611154176794 -3.52025806209735,39.5652800555973 -3.52807566490399,39.5678468969798 -3.53655942414493,39.5687172638333 -3.5453833334101))"))

(def fixture-filtering-providers-tags
  [[:users
    [{:id owner-id :email "jdoe@example.org"}]]
   [:providers_set
    [{:id 1 :name "First" "owner-id" owner-id "last-version" 2}
     {:id 2 :name "Bar" "owner-id" owner-id "last-version" 2}]]
   [:regions
    [{:id 1 :country "AAA" :name "Aaa" "the_geom" (sample-multipolygon)}]]
   [:projects2
    [{:id 1 "owner-id" owner-id :name "foo" :provider-set-id 1 :region-id 1}
     {:id 2 "owner-id" owner-id :name "foo2" :provider-set-id 2 :region-id 1}]]
   [:providers
    [{:id 13 :lon 23.416667 :version 2 :the_geom (point "SRID=4326; POINT(23.416667 -19.983333)") "source-id" 1
      "processing-status" ":ok" "provider-set-id" 1 :tags "private laboratory radiography pharmacy"
      :type "hospital" :lat -19.983333 :capacity 50 :name "Nanyuki Cottage Hospital"}
     {:id 14 :lon 36.72609 :version 2 :the_geom (point "SRID=4326;POINT(36.72609 -1.336063)")
      "source-id" 2 "processing-status" ":ok" "provider-set-id" 1 :tags "private general-wards pediatric-ward 24hs-pharmacy "
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

(def fixture-delete-provider-set
  [[:users
    [{:id owner-id :email "jdoe@example.org"}]]
   [:regions
    [{:id 1 :country "AAA" :name "Aaa" "the_geom" (sample-multipolygon)}]]
   [:providers_set
    [{:id 1 :name "First" "owner-id" owner-id "last-version" 1}
     {:id 2 :name "Bar" "owner-id" owner-id "last-version" 1}]]
   [:projects2
    [{:id 1 "owner-id" owner-id :name "foo" :provider-set-id 1 :region-id 1}]]
   [:providers
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
    (let [store                         (:planwise.component/providers-set system)
          provider-set1-id              (:id (providers-set/create-provider-set store "Initial" owner-id :none))
          provider-set2-id              (:id (providers-set/create-provider-set store "Other" owner-id :none))
          facilities-provider-set1      (providers-set/csv-to-providers store provider-set1-id (io/resource "sites.csv"))
          version-provider-set1         (:last-version (providers-set/get-provider-set store provider-set1-id))
          version-provider-set2         (:last-version (providers-set/get-provider-set store provider-set2-id))
          listed-providers-set1         (providers-set/providers-by-version store provider-set1-id version-provider-set1)
          listed-providers-set2         (providers-set/providers-by-version store provider-set2-id version-provider-set2)]
      (is (= (count listed-providers-set1) 4)
          (is (= (count listed-providers-set2) 0))))))

(defn- pd [v] (do (println v) v))

(deftest several-csv-to-provider-set
  (test-system/with-system (test-config)
    (let [store                        (:planwise.component/providers-set system)
          provider-set-id              (pd (:id (providers-set/create-provider-set store "Initial" owner-id :none)))
          providers                    (providers-set/csv-to-providers store provider-set-id (io/resource "sites.csv"))
          other-providers              (providers-set/csv-to-providers store provider-set-id (io/resource "other-sites.csv"))
          last-version-provider-set    (:last-version (providers-set/get-provider-set store provider-set-id))
          listed-providers             (providers-set/providers-by-version store provider-set-id last-version-provider-set)]
      (is (= (count listed-providers) 2))
      (is (= last-version-provider-set 2)))))

;; ----------------------------------------------------------------------
;; Testing site's tag filtering

(defn- validate-filter-count
  [store id tags number]
  (is (= (:filtered (providers-set/count-providers-filter-by-tags store id 1 tags)) number)))

(deftest filtering-providers
  (test-system/with-system (test-config fixture-filtering-providers-tags)
    (let [store                    (:planwise.component/providers-set system)
          providers-id1            (providers-set/providers-by-version store 1 2)
          number                   (count providers-id1)]
      (validate-filter-count store 1 [""] number)
      (validate-filter-count store 1 ["inexistent"] 0)
      (validate-filter-count store 1 ["private"] 2)
      (validate-filter-count store 2 ["private"] 0)
      (validate-filter-count store 2 ["-"] 0))))

;; ----------------------------------------------------------------------
;; Testing deleting provider-set

(defn- delete-provider-set-and-catch-exception
  [store id]
  (try
    (providers-set/delete-provider-set store id)
    (catch Exception e
      (ex-data e))))

(deftest delete-provider-set
  (test-system/with-system (test-config fixture-delete-provider-set)
    (let [store            (:planwise.component/providers-set system)
          provider-set-id2 (providers-set/get-provider-set store 2)]
      (= (some? provider-set-id2))
      (let [_                (delete-provider-set-and-catch-exception store 2)
            provider-set-id2 (providers-set/get-provider-set store 2)
            _                (delete-provider-set-and-catch-exception store 1)
            provider-set-id1 (providers-set/get-provider-set store 1)]
        (= (nil? provider-set-id2))
        (= (map? provider-set-id1))))))
