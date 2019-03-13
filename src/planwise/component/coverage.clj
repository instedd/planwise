(ns planwise.component.coverage
  (:require [planwise.boundary.coverage :as boundary]
            [planwise.component.coverage.pgrouting :as pgrouting]
            [planwise.component.coverage.simple :as simple]
            [planwise.component.coverage.rasterize :as rasterize]
            [planwise.component.coverage.friction :as friction]
            [planwise.util.geo :as geo]
            [integrant.core :as ig]
            [clojure.spec.alpha :as s]
            [clojure.java.io :as io]
            [hugsql.core :as hugsql]))

(hugsql/def-db-fns "planwise/sql/coverage/coverage.sql")

;; Specs

(s/def ::algorithm keyword?)
(s/def ::raster string?)

(s/def ::base-criteria (s/keys :req-un [::algorithm]
                               :opt-un [::raster ::rasterize/ref-coords ::rasterize/resolution]))

(s/def ::driving-time #{30 60 90 120})
(s/def ::pgrouting-alpha-criteria (s/keys :req-un [::driving-time]))
(s/def ::driving-friction-criteria (s/keys :req-un [::driving-time]))

(s/def ::distance #{5 10 20 50 100})
(s/def ::simple-buffer-criteria (s/keys :req-un [::distance]))

(s/def ::walking-time #{60 120 180})
(s/def ::walking-friction-criteria (s/keys :req-un [::walking-time]))

(defmulti criteria-algo :algorithm)
(defmethod criteria-algo :pgrouting-alpha [_]
  (s/merge ::base-criteria ::pgrouting-alpha-criteria))
(defmethod criteria-algo :simple-buffer [_]
  (s/merge ::base-criteria ::simple-buffer-criteria))
(defmethod criteria-algo :walking-friction [_]
  (s/merge ::base-criteria ::walking-friction-criteria))
(defmethod criteria-algo :driving-friction [_]
  (s/merge ::base-criteria ::driving-friction-criteria))

(s/def ::criteria (s/multi-spec criteria-algo :algorithm))


;; Supported algorithm description

(def supported-algorithms
  {:pgrouting-alpha
   {:label       "Travel by car (pgRouting)"
    :description "Computes all reachable OSM nodes from the nearest to the starting point and then applies the alpha shape algorithm to the resulting points"
    :criteria    {:driving-time {:label   "Driving time"
                                 :type    :enum
                                 :options [{:value 30  :label "30 minutes"}
                                           {:value 60  :label "1 hour"}
                                           {:value 90  :label "1:30 hours"}
                                           {:value 120 :label "2 hours"}]}}}

   :driving-friction
   {:label       "Travel by car"
    :description "Computes reachable isochrone using a friction raster layer"
    :criteria    {:driving-time {:label   "Driving time"
                                 :type    :enum
                                 :options [{:value 30  :label "30 minutes"}
                                           {:value 60  :label "1 hour"}
                                           {:value 90  :label "1:30 hours"}
                                           {:value 120 :label "2 hours"}]}}}

   :walking-friction
   {:label       "Walking distance"
    :description "Computes reachable isochrone using a friction raster layer"
    :criteria    {:walking-time {:label   "Walking time"
                                 :type    :enum
                                 :options [{:value 60  :label "1 hour"}
                                           {:value 120 :label "2 hours"}
                                           {:value 180 :label "3 hours"}]}}}

   :simple-buffer
   {:label       "Distance buffer"
    :description "Simple buffer around origin for testing purposes only"
    :criteria    {:distance {:label   "Distance"
                             :type    :enum
                             :options [{:value 5   :label "5 km"}
                                       {:value 10  :label "10 km"}
                                       {:value 20  :label "20 km"}
                                       {:value 50  :label "50 km"}
                                       {:value 100 :label "100 km"}]}}}})

;; Coverage computation

(defmulti compute-coverage-polygon (fn [service coords criteria] (:algorithm criteria)))

(defmethod compute-coverage-polygon :default
  [service coords criteria]
  (throw (IllegalArgumentException. "Missing or invalid coverage algorithm")))

(defmethod compute-coverage-polygon :pgrouting-alpha
  [{:keys [db]} coords criteria]
  (let [db-spec   (:spec db)
        pg-point  (geo/make-pg-point coords)
        threshold (:driving-time criteria)
        result    (pgrouting/compute-coverage db-spec pg-point threshold)]
    (case (:result result)
      "ok" (:polygon result)
      (throw (ex-info "pgRouting coverage computation failed"  {:causes (:result result) :coords coords})))))

(defmethod compute-coverage-polygon :simple-buffer
  [{:keys [db]} coords criteria]
  (let [db-spec  (:spec db)
        pg-point (geo/make-pg-point coords)
        distance (* 1000 (:distance criteria))
        result   (simple/compute-coverage db-spec pg-point distance)]
    (case (:result result)
      "ok" (:polygon result)
      (throw (ex-info "Simple buffer coverage computation failed" {:causes (:result result) :coords coords})))))

(defmethod compute-coverage-polygon :walking-friction
  [{:keys [db runner]} coords criteria]
  (let [db-spec         (:spec db)
        friction-raster (friction/find-friction-raster db-spec coords)
        max-time        (:walking-time criteria)
        min-friction    (float (/ 1 100))]
    (if friction-raster
      (friction/compute-polygon runner friction-raster coords max-time min-friction)
      (throw (ex-info "Cannot find a friction raster for the given coordinates" {:coords coords})))))

(defmethod compute-coverage-polygon :driving-friction
  [{:keys [db runner]} coords criteria]
  (let [db-spec         (:spec db)
        friction-raster (friction/find-friction-raster db-spec coords)
        max-time        (:driving-time criteria)
        min-friction    (float (/ 1 2000))]
    (if friction-raster
      (friction/compute-polygon runner friction-raster coords max-time min-friction)
      (throw (ex-info "Cannot find a friction raster for the given coordinates" {:coords coords})))))

(defn geometry-intersected-with-project-region
  [{:keys [db]} geometry region-id]
  (let [db-spec (:spec db)]
    (db-intersected-coverage-region db-spec {:geom geometry
                                             :region-id region-id})))

(defn geometry-intersected-with-project-region
  [{:keys [db]} geometry region-id]
  (let [db-spec (:spec db)]
    (db-intersected-coverage-region db-spec {:geom geometry
                                             :region-id region-id})))

(defn locations-outside-polygon
  [{:keys [db]} polygon locations]
  (remove (fn [[lon lat _]] (:cond (db-inside-geometry (:spec db) {:lon lon
                                                                   :lat lat
                                                                   :geom polygon})))
          locations))

(defn get-max-distance-from-geometry
  [{:keys [db] :as cov} polygon]
  (:maxdist (db-get-max-distance (:spec db) {:geom polygon})))

(def default-grid-align-options
  {:ref-coords {:lat 0 :lon 0}
   :resolution {:x-res 1/1200 :y-res 1/1200}})

(defrecord CoverageService [db runner]
  boundary/CoverageService
  (supported-algorithms [this]
    supported-algorithms)
  (compute-coverage [this coords criteria]
    (s/assert ::geo/coords coords)
    (s/assert ::criteria criteria)
    (let [polygon (compute-coverage-polygon this coords criteria)
          raster-options (merge default-grid-align-options criteria)]
      (when-let [raster-path (:raster criteria)]
        (io/make-parents raster-path)
        (rasterize/rasterize polygon raster-path raster-options))
      polygon))
  (geometry-intersected-with-project-region [this geometry region-id]
    (geometry-intersected-with-project-region this geometry region-id))
  (locations-outside-polygon [this polygon locations]
    (locations-outside-polygon this polygon locations))
  (get-max-distance-from-geometry [this geometry]
    (get-max-distance-from-geometry this geometry)))


(defmethod ig/init-key :planwise.component/coverage
  [_ config]
  (map->CoverageService config))


;; REPL testing

(comment

  (s/check-asserts true)

  (def service (second (ig/find-derived-1 integrant.repl.state/system :planwise.component/coverage)))

  (boundary/supported-algorithms service)

  (time
   (boundary/compute-coverage service
                              {:lat -3.0361 :lon 40.1333}
                              {:algorithm :pgrouting-alpha
                               :driving-time 60
                               :raster "/tmp/pgr.tif"}))

  (time
   (boundary/compute-coverage service
                              {:lat -3.0361 :lon 40.1333}
                              {:algorithm :simple-buffer
                               :distance 20
                               :raster "/tmp/buffer.tif"}))

  (time
   (boundary/compute-coverage service
                              {:lat -1.2741 :lon 36.7931}
                              {:algorithm :pgrouting-alpha
                               :driving-time 60
                               :raster "/tmp/nairobi.tif"
                               :ref-coords {:lat 5.4706946 :lon 33.9126084}
                               :resolution {:x-res 0.0008333 :y-res 0.0008333}})))
