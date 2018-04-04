(ns planwise.component.facilities-test
  (:require [clojure.test :refer :all]
            [planwise.boundary.facilities :as facilities]
            [planwise.test-system :as test-system]
            [clojure.java.jdbc :as jdbc]
            [integrant.core :as ig])
  (:import [org.postgis PGgeometry]))

(defn make-point [lat lon]
  (PGgeometry. (str "SRID=4326;POINT(" lon " " lat ")")))
(defn sample-polygon []
  (PGgeometry. (str "SRID=4326;POLYGON((0 0, 0 1, 1 1, 1 0, 0 0))")))

(def dataset-id 1)

(def fixture-data
  [[:users
    [{:id 1 :email "example@instedd.org"}]]
   [:datasets
    [{:id 1 :name "Test Dataset" :collection_id 123 :owner_id 1}]]
   [:facility_types
    [{:id 1 :dataset_id dataset-id :name "Hospital" :code "hospital"}]]
   [:facilities
    [{:id 1 :dataset_id dataset-id :site_id 1 :name "Facility A" :type_id 1 :lat -3   :lon 42 :the_geom (make-point -3 42)}
     {:id 2 :dataset_id dataset-id :site_id 2 :name "Facility B" :type_id 1 :lat -3.5 :lon 42 :the_geom (make-point -3.5 42)}]]
   [:facilities_polygons
    [{:id 1 :facility_id 1 :threshold 900 :method "alpha-shape" :the_geom (sample-polygon)}]]])

(def new-facilities
  [{:site-id 3 :name "New facility" :type-id 1 :lat 4 :lon 10 :type "hospital" :capacity 10}])

(defn test-config
  ([]
   (test-config fixture-data))
  ([data]
   (test-system/config
    {:planwise.test/fixtures {:fixtures data}
     :planwise.component/facilities {:db (ig/ref :duct.database/sql)}})))

(deftest list-facilities
  (test-system/with-system (test-config)
    (let [service (:planwise.component/facilities system)
          facilities (facilities/list-facilities service dataset-id)]
      (is (= 2 (count facilities)))
      (is (= #{:id :name :lat :lon} (-> facilities first keys set))))))

(deftest insert-facility
  (test-system/with-system (test-config)
    (let [service (:planwise.component/facilities system)]
      (test-system/execute-sql system "ALTER SEQUENCE facilities_id_seq RESTART WITH 100")
      (is (= [100] (map :id (facilities/insert-facilities! service dataset-id new-facilities))))
      (is (= 3 (count (facilities/list-facilities service dataset-id)))))))

(deftest destroy-facilities
  (test-system/with-system (test-config)
    (let [service (:planwise.component/facilities system)]
      (facilities/destroy-facilities! service dataset-id {})
      (is (= 0 (count (facilities/list-facilities service dataset-id)))))))

(deftest list-isochrones-in-bbox
  (test-system/with-system (test-config)
    (let [service (:planwise.component/facilities system)
          facilities (facilities/isochrones-in-bbox service dataset-id {:threshold 900} {:bbox [0.0 0.0 2.0 2.0]})]
      (is (= 1 (count facilities)))
      (let [[facility] facilities]
        (is (= 1 (:id facility)))
        (is (= 1 (:polygon-id facility)))
        (is (:isochrone facility))))))

(deftest list-isochrones-in-bbox-excluding-ids
  (test-system/with-system (test-config)
    (let [service (:planwise.component/facilities system)
          facilities (facilities/isochrones-in-bbox service dataset-id {:threshold 900} {:bbox [0.0 0.0 2.0 2.0], :excluding [1]})]
      (is (= 1 (count facilities)))
      (let [[facility] facilities]
        (is (= 1 (:id facility)))
        (is (= 1 (:polygon-id facility)))
        (is (nil? (:isochrone facility)))))))

(def multiple-facilities-fixture-data
  [[:users
    [{:id 1 :email "example@instedd.org"}]]
   [:datasets
    [{:id 1 :name "Test Dataset" :collection_id 123 :owner_id 1}
     {:id 2 :name "Test Dataset 2" :collection_id 124 :owner_id 1}]]
   [:facility_types
    [{:id 1 :dataset_id 1 :name "Hospital", :code "hospital"}
     {:id 2 :dataset_id 1 :name "Rural", :code "rural"}
     {:id 3 :dataset_id 2 :name "General", :code "general"}]]
   [:regions
    [{:id 1 :country "C" :name "R1" :the_geom (PGgeometry. (str "SRID=4326;MULTIPOLYGON(((0 0, 0 1, 1 1, 1 0, 0 0)))"))}
     {:id 2 :country "C" :name "R2" :the_geom (PGgeometry. (str "SRID=4326;MULTIPOLYGON(((2 2, 2 3, 3 3, 3 2, 2 2)))"))}]]
   [:facilities
    [{:id 1 :dataset_id 1 :site_id 1 :name "Facility A" :type_id 1 :lat 0.5 :lon 0.5 :the_geom (make-point 0.5 0.5) :processing_status "ok" :capacity 500}
     {:id 2 :dataset_id 2 :site_id 2 :name "Facility B" :type_id 1 :lat 0.5 :lon 0.5 :the_geom (make-point 0.5 0.5) :processing_status "ok"}
     {:id 3 :dataset_id 1 :site_id 3 :name "Facility C" :type_id 2 :lat 0.5 :lon 0.5 :the_geom (make-point 0.5 0.5) :processing_status "ok" :capacity 200}
     {:id 4 :dataset_id 1 :site_id 4 :name "Facility D" :type_id 1 :lat 2.5 :lon 2.5 :the_geom (make-point 2.5 2.5) :processing_status "ok"}]]
   [:facilities_polygons
    [{:id 1 :facility_id 1 :threshold 900 :method "alpha-shape" :the_geom (sample-polygon) :population 2000}
     {:id 2 :facility_id 1 :threshold 100 :method "alpha-shape" :the_geom (sample-polygon)}
     {:id 3 :facility_id 1 :threshold 900 :method "buffer"      :the_geom (sample-polygon)}
     {:id 4 :facility_id 2 :threshold 900 :method "alpha-shape" :the_geom (sample-polygon)}
     {:id 5 :facility_id 3 :threshold 900 :method "alpha-shape" :the_geom (sample-polygon)}
     {:id 6 :facility_id 4 :threshold 900 :method "alpha-shape" :the_geom (sample-polygon)}]]
   [:facilities_polygons_regions
    [{:facility_polygon_id 1 :region_id 1 :population 1000}
     {:facility_polygon_id 2 :region_id 1}
     {:facility_polygon_id 3 :region_id 1}
     {:facility_polygon_id 4 :region_id 1}
     {:facility_polygon_id 5 :region_id 1}
     {:facility_polygon_id 6 :region_id 1}
     {:facility_polygon_id 1 :region_id 2}]]])

(deftest polygons-in-region
  (test-system/with-system (test-config multiple-facilities-fixture-data)
    (let [service (:planwise.component/facilities system)
          polygons (facilities/polygons-in-region service 1 {:threshold 900 :algorithm "alpha-shape"} {:region 1 :types [1]})]
      (is (= 1 (count polygons)))
      (let [[{:keys [facility-polygon-id facility-population facility-region-population capacity]}] polygons]
        (is (= 1 facility-polygon-id))
        (is (= 2000 facility-population))
        (is (= 1000 facility-region-population))
        (is (= 500 capacity))))))

(deftest insert-facility-types
  (let [new-types [{:name "General" :code "general"}
                   {:name "Hospital" :code "hospital"}]]
    (test-system/with-system (test-config multiple-facilities-fixture-data)
      (test-system/execute-sql system "ALTER SEQUENCE facility_types_id_seq RESTART WITH 100")
      (let [service        (:planwise.component/facilities system)
            result         (facilities/insert-types! service 1 new-types)
            all-types      (facilities/list-types service 1)]
        (is (= result
               [{:id 100, :name "General",  :code "general"}
                {:id 1,   :name "Hospital", :code "hospital"}]))
        (is (= (set all-types)
               #{{:id 1,   :name "Hospital", :code "hospital"}
                 {:id 2,   :name "Rural",    :code "rural"}
                 {:id 100, :name "General",  :code "general"}}))))
    (test-system/with-system (test-config multiple-facilities-fixture-data)
      (test-system/execute-sql system "ALTER SEQUENCE facility_types_id_seq RESTART WITH 100")
      (let [service        (:planwise.component/facilities system)
            reverse-result (facilities/insert-types! service 1 (reverse new-types))]
        (is (= reverse-result
               [{:id 1,   :name "Hospital", :code "hospital"}
                {:id 100, :name "General",  :code "general"}]))))))

(deftest upsert-facilities
  (test-system/with-system (test-config multiple-facilities-fixture-data)
    (test-system/execute-sql system "ALTER SEQUENCE facilities_id_seq RESTART WITH 100")
    (let [service (:planwise.component/facilities system)
          data    [{:site-id 1 :name "Facility A" :type-id 1 :capacity 500 :lat 0.5 :lon 0.5}  ; existing
                   {:site-id 2 :name "Facility B" :type-id 1 :capacity 0   :lat 0.5 :lon 0.5}  ; new
                   {:site-id 3 :name "Updated C"  :type-id 2 :capacity 100 :lat 0.5 :lon 0.5}  ; updated
                   {:site-id 4 :name "Updated D"  :type-id 2 :capacity 0   :lat 1.5 :lon 1.5}] ; moved
          result  (facilities/insert-facilities! service dataset-id data)
          list    (facilities/list-facilities service dataset-id {})
          {[existing] 1, [new] 100, [updated] 3, [moved] 4} (group-by :id list)]
      (letfn [(select-attrs [f] (-> f
                                    (select-keys [:id :name :site-id :type-id :lat :lon :processing-status :capacity])
                                    (update :lat float)
                                    (update :lon float)))]
        ; Check that the result from insertion yields the same data as the result from a select
        (is (= (map select-attrs (sort-by :id result))
               (map select-attrs (sort-by :id list))))
        ; Check the insertion-status for each facility
        (is (= [[1 :existing] [3 :updated] [4 :moved] [100 :new]]
               (map (juxt :id :insertion-status) (sort-by :id result))))
        ; Check the inserted attributes of each facility individually
        (is (= {:id 1   :site-id 1 :name "Facility A" :type-id 1 :lat 0.5 :lon 0.5 :capacity 500 :processing-status "ok"}
               (select-attrs existing)))
        (is (= {:id 100 :site-id 2 :name "Facility B" :type-id 1 :lat 0.5 :lon 0.5 :capacity 0   :processing-status nil}
               (select-attrs new)))
        (is (= {:id 3   :site-id 3 :name "Updated C"  :type-id 2 :lat 0.5 :lon 0.5 :capacity 100 :processing-status "ok"}
               (select-attrs updated)))
        (is (= {:id 4   :site-id 4 :name "Updated D"  :type-id 2 :lat 1.5 :lon 1.5 :capacity 0   :processing-status nil}
               (select-attrs moved)))))))

;; (facilities/list-facilities (:planwise.component/facilities integrant.repl.state/system) 1 {})
