(ns planwise.component.coverage
  (:require [planwise.boundary.coverage :as boundary]
            [planwise.component.coverage.pgrouting :as pgrouting]
            [planwise.component.coverage.simple :as simple]
            [planwise.component.coverage.rasterize :as rasterize]
            [planwise.util.pg :as pg]
            [integrant.core :as ig]))


(def supported-algorithms
  {:pgrouting-alpha
   {:label       "Travel by car"
    :description "Computes all reachable OSM nodes from the nearest to the
                  starting point and then applies the alpha shape algorithm to
                  the resulting points"
    :criteria    {:driving-time {:label   "Driving time"
                                 :type    :enum
                                 :options [{:value 30  :label "30 minutes"}
                                           {:value 60  :label "1 hour"}
                                           {:value 90  :label "1:30 hours"}
                                           {:value 120 :label "2 hours"}]}}}

   :simple-buffer
   {:label       "Distance buffer"
    :description "Simple buffer around origin for testing purposes only"
    :criteria    {:distance {:label "Distance (meters)"
                             :type  :number}}}})

(defmulti compute-coverage-polygon (fn [service point criteria] (:algorithm criteria)))

(defmethod compute-coverage-polygon :default
  [service point criteria]
  (throw (IllegalArgumentException. "Missing or invalid coverage algorithm")))

(defmethod compute-coverage-polygon :pgrouting-alpha
  [{:keys [db]} point criteria]
  (let [db-spec   (:spec db)
        pg-point  (pg/make-point point)
        threshold (:driving-time criteria)
        result    (pgrouting/compute-coverage db-spec pg-point threshold)]
    (case (:result result)
      "ok" (:polygon result)
      (throw (RuntimeException. (str "pgRouting coverage computation failed: " (:result result)))))))

(defmethod compute-coverage-polygon :simple-buffer
  [{:keys [db]} point criteria]
  (let [db-spec   (:spec db)
        pg-point  (pg/make-point point)
        distance  (:distance criteria)
        result    (simple/compute-coverage db-spec pg-point distance)]
    (case (:result result)
      "ok" (:polygon result)
      (throw (RuntimeException. (str "Simple buffer coverage computation failed: " (:result result)))))))

(defrecord CoverageService [db]
  boundary/CoverageService
  (supported-algorithms [this]
    supported-algorithms)
  (compute-coverage [this point criteria]
    (let [polygon (compute-coverage-polygon this point criteria)
          raster-options (merge {:ref-coords {:lat 0 :lng 0}
                                 :resolution {:x-res 1/1200 :y-res 1/1200}}
                                criteria)]
      (when-let [raster-path (:raster criteria)]
        (rasterize/rasterize polygon raster-path raster-options))
      polygon)))


(defmethod ig/init-key :planwise.component/coverage
  [_ config]
  (map->CoverageService config))


;; REPL testing

(comment

  (def service (second (ig/find-derived-1 integrant.repl.state/system :planwise.component/coverage)))

  (boundary/supported-algorithms service)

  (boundary/compute-coverage service
                             {:lat -3.0361 :lon 40.1333}
                             {:algorithm :pgrouting-alpha
                              :driving-time 60
                              :raster "/tmp/pgr.tif"})

  (boundary/compute-coverage service
                             {:lat -3.0361 :lon 40.1333}
                             {:algorithm :simple-buffer
                              :distance 30000
                              :raster "/tmp/buffer.tif"})
  )
