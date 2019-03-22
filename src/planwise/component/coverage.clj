(ns planwise.component.coverage
  (:require [planwise.boundary.coverage :as boundary]
            [planwise.component.coverage.simple :as simple]
            [planwise.component.coverage.rasterize :as rasterize]
            [planwise.component.coverage.friction :as friction]
            [planwise.util.geo :as geo]
            [integrant.core :as ig]
            [clojure.spec.alpha :as s]
            [clojure.java.io :as io]
            [clojure.edn :as edn]
            [hugsql.core :as hugsql]))

(hugsql/def-db-fns "planwise/sql/coverage/coverage.sql")

;; Specs =====================================================================
;;

(s/def ::driving-time #{30 60 90 120})
(s/def ::driving-friction-criteria (s/keys :req-un [::driving-time]))

(s/def ::distance #{5 10 20 50 100})
(s/def ::simple-buffer-criteria (s/keys :req-un [::distance]))

(s/def ::walking-time #{60 120 180})
(s/def ::walking-friction-criteria (s/keys :req-un [::walking-time]))

(defmethod boundary/criteria-algo :simple-buffer [_]
  (s/merge ::boundary/base-criteria ::simple-buffer-criteria))
(defmethod boundary/criteria-algo :walking-friction [_]
  (s/merge ::boundary/base-criteria ::walking-friction-criteria))
(defmethod boundary/criteria-algo :driving-friction [_]
  (s/merge ::boundary/base-criteria ::driving-friction-criteria))


;; Supported algorithm description ===========================================
;;

(def supported-algorithms
  {:driving-friction
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


;; Coverage algorithms =======================================================
;;

(defmulti compute-coverage-polygon (fn [service coords criteria] (:algorithm criteria)))

(defmethod compute-coverage-polygon :default
  [service coords criteria]
  (throw (IllegalArgumentException. "Missing or invalid coverage algorithm")))

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


;; Other utility functions ===================================================
;;

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
   :resolution {:xres 1/1200 :yres -1/1200}})


;; Context related functionality =============================================
;;

(defn- build-sql-id
  [id]
  (let [sql-id (if (string? id) id (pr-str id))]
    (when (> (count sql-id) 254)
      (throw (ex-info "Coverage key too large" {:id id :sql-id sql-id})))
    sql-id))

(defn- db->context
  [context]
  (-> context
      (update :options edn/read-string)))

(defn- context->db
  [context]
  (-> context
      (update :options pr-str)))

(defn- select-context
  [db cid]
  (some->
   (db-select-context (:spec db) {:id cid})
   db->context))

(defn- insert-context
  [db context]
  (db-insert-context! (:spec db) (context->db context))
  context)

(defn- destroy-context
  [{:keys [db]} context-id]
  (let [cid (build-sql-id context-id)]
    ;; TODO: remove any coverage raster files associated with the context
    (let [result (db-delete-context! (:spec db) {:id cid})]
      (= 1 (first result)))))

(defn- setup-context
  [{:keys [db] :as service} context-id options]
  (let [cid              (build-sql-id context-id)
        new-context      {:id cid :options options}
        existing-context (db-select-context (:spec db) {:id cid})]
    (s/assert ::boundary/context-options options)
    (cond
      (and existing-context (= options (:options existing-context)))
      existing-context

      (some? existing-context)
      (do
        (destroy-context service context-id)
        (insert-context db new-context))

      :else
      (insert-context db new-context))))

(defn- resolve-coverages
  [{:keys [db]} context-id locations]
  (let [cid     (build-sql-id context-id)
        context (select-context cid)]
    (when (nil? context)
      (throw (ex-info "Context has not been setup; cannot resolve coverages" {:context-id context-id})))
    ))


;; Service definition ========================================================
;;

(defrecord CoverageService [db runner])

(defmethod ig/init-key :planwise.component/coverage
  [_ config]
  (map->CoverageService config))


(extend-protocol boundary/CoverageService
  CoverageService
  (supported-algorithms [this]
    supported-algorithms)
  (compute-coverage-polygon [this coords criteria]
    (compute-coverage-polygon this coords criteria)))

(extend-protocol boundary/CoverageUtilities
  CoverageService
  (locations-outside-polygon [this polygon locations]
    (locations-outside-polygon this polygon locations))
  (geometry-intersected-with-project-region [this geometry region-id]
    (geometry-intersected-with-project-region this geometry region-id))
  (get-max-distance-from-geometry [this geometry]
    (get-max-distance-from-geometry this geometry)))


(extend-protocol boundary/CoverageContexts
  CoverageService
  (setup-context [this context-id options]
    (setup-context this context-id options))

  (destroy-context [this context-id]
    (destroy-context this context-id))

  (resolve-coverages! [this context-id locations])

  (query-coverages [this context-id ids query]))


;; REPL testing ==============================================================
;;

(comment

  (s/check-asserts true)

  (def service (second (ig/find-derived-1 integrant.repl.state/system :planwise.component/coverage)))

  (boundary/supported-algorithms service)

  (time
   (boundary/compute-coverage service
                              {:lat -3.0361 :lon 40.1333}
                              {:algorithm :simple-buffer
                               :distance 20
                               :raster "/tmp/buffer.tif"}))

  (time
   (boundary/compute-coverage service
                              {:lat -1.2741 :lon 36.7931}
                              {:algorithm :driving-friction
                               :driving-time 60
                               :raster "/tmp/nairobi.tif"
                               :ref-coords {:lat 5.4706946 :lon 33.9126084}
                               :resolution {:xres 0.0008333 :yres 0.0008333}}))

  (boundary/setup-context (dev/coverage) [:project 1]
                          {:region-id 1 :coverage-criteria {:algorithm :driving-friction :driving-time 30}})

  (boundary/destroy-context (dev/coverage) [:project 1])

  (boundary/resolve-coverages! (dev/coverage) [:project 1]
                               [{:id [:provider 1] :lat 8.5 :lon 18.1}
                                {:id [:provider 2] :lat 9.1 :lon 16.3}])

  nil)
