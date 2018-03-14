(ns planwise.component.coverage
  (:require [planwise.boundary.coverage :as boundary]
            [planwise.component.coverage.pgrouting :as pgrouting]
            [planwise.component.coverage.simple :as simple]
            [planwise.component.coverage.rasterize :as rasterize]
            [planwise.util.pg :as pg]
            [integrant.core :as ig]
            [clojure.spec.alpha :as s]))

;; Specs

(s/def ::algorithm keyword?)
(s/def ::raster string?)

(s/def ::base-criteria (s/keys :req-un [::algorithm]
                               :opt-un [::rasterize/ref-coords ::rasterize/resolution]))

(s/def ::driving-time #{30 60 90 120})
(s/def ::pgrouting-alpha-criteria (s/keys :req-un [::driving-time]))

(s/def ::distance #{5 10 20 50 100})
(s/def ::simple-buffer-criteria (s/keys :req-un [::distance]))

(defmulti criteria-algo :algorithm)
(defmethod criteria-algo :pgrouting-alpha [_]
  (s/merge ::base-criteria ::pgrouting-alpha-criteria))
(defmethod criteria-algo :simple-buffer [_]
  (s/merge ::base-criteria ::simple-buffer-criteria))

(s/def ::criteria (s/multi-spec criteria-algo :algorithm))


;; Supported algorithm description

(def supported-algorithms
  {:pgrouting-alpha
   {:label       "Travel by car"
    :description "Computes all reachable OSM nodes from the nearest to the starting point and then applies the alpha shape algorithm to the resulting points"
    :criteria    {:driving-time {:label   "Driving time"
                                 :type    :enum
                                 :options [{:value 30  :label "30 minutes"}
                                           {:value 60  :label "1 hour"}
                                           {:value 90  :label "1:30 hours"}
                                           {:value 120 :label "2 hours"}]}}}

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
        pg-point  (pg/make-point coords)
        threshold (:driving-time criteria)
        result    (pgrouting/compute-coverage db-spec pg-point threshold)]
    (case (:result result)
      "ok" (:polygon result)
      (throw (RuntimeException. (str "pgRouting coverage computation failed: " (:result result)))))))

(defmethod compute-coverage-polygon :simple-buffer
  [{:keys [db]} coords criteria]
  (let [db-spec  (:spec db)
        pg-point (pg/make-point coords)
        distance (* 1000 (:distance criteria))
        result   (simple/compute-coverage db-spec pg-point distance)]
    (case (:result result)
      "ok" (:polygon result)
      (throw (RuntimeException. (str "Simple buffer coverage computation failed: " (:result result)))))))

(def default-grid-align-options
  {:ref-coords {:lat 0 :lon 0}
   :resolution {:x-res 1/1200 :y-res 1/1200}})

(defrecord CoverageService [db]
  boundary/CoverageService
  (supported-algorithms [this]
    supported-algorithms)
  (compute-coverage [this coords criteria]
    (s/assert ::pg/coords coords)
    (s/assert ::criteria criteria)
    (let [polygon (compute-coverage-polygon this coords criteria)
          raster-options (merge default-grid-align-options criteria)]
      (when-let [raster-path (:raster criteria)]
        (rasterize/rasterize polygon raster-path raster-options))
      polygon)))


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
                               :resolution {:x-res 0.0008333 :y-res 0.0008333}}))
  )
