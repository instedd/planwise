(ns planwise.component.coverage
  (:require [planwise.boundary.coverage :as boundary]
            [planwise.boundary.file-store :as file-store]
            [planwise.component.coverage.simple :as simple]
            [planwise.component.coverage.rasterize :as rasterize]
            [planwise.component.coverage.friction :as friction]
            [planwise.util.geo :as geo]
            [integrant.core :as ig]
            [clojure.spec.alpha :as s]
            [clojure.java.io :as io]
            [clojure.edn :as edn]
            [clojure.set :refer [rename-keys]]
            [clojure.string :as str]
            [hugsql.core :as hugsql]
            [taoensso.timbre :as timbre]))

(timbre/refer-timbre)

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

(defn- raster-file-name
  [id]
  (str (file-store/build-file-id id) ".tif"))

(defn- db->context
  [context]
  (-> context
      (rename-keys {:region_id :region-id})
      (update :options edn/read-string)))

(defn- context->db
  [context]
  (-> context
      (update :options pr-str)))

(defn- select-context
  [db cid]
  (some->
   (db-select-context (:spec db) {:cid cid})
   db->context))

(defn- insert-context
  [db context]
  (db-insert-context! (:spec db) (context->db context))
  context)

(defn- destroy-context
  [{:keys [db file-store]} context-id]
  (let [cid (build-sql-id context-id)]
    (file-store/destroy-collection file-store :coverages context-id)
    (let [result (db-delete-context! (:spec db) {:cid cid})]
      (= 1 (first result)))))

(defn- setup-context
  [{:keys [db] :as service} context-id options]
  (s/assert ::boundary/context-options options)
  (let [cid              (build-sql-id context-id)
        region-id        (:region-id options)
        new-context      {:cid cid :region-id region-id :options options}
        existing-context (select-context db cid)]
    (cond
      (and existing-context (= options (:options existing-context)))
      existing-context

      (some? existing-context)
      (do
        (destroy-context service context-id)
        (insert-context db new-context))

      :else
      (insert-context db new-context))))

(defn- check-coverage-exists?
  [db coverage]
  (let [result (db-check-coverage (:spec db) coverage)]
    (and (some? result) (< (:distance result) 10e-5))))

(defn- check-outside-region?
  [db coverage]
  (let [result (db-check-inside-region (:spec db) coverage)]
    (not (:inside result))))

(defn- upsert-coverage!
  [db coverage]
  (db-upsert-coverage! (:spec db) coverage))

(defn- clip-polygon
  [db context polygon]
  (let [region-id (:region-id context)
        result    (db-clip-polygon (:spec db) {:region-id region-id
                                               :polygon   polygon})]
    (if result
      (:clipped-polygon result)
      (throw (ex-info "failed to clip polygon to context region" {:context context
                                                                  :polygon polygon})))))

(defn- resolve-coverage!
  [{:keys [db] :as service} context location]
  (let [id                (:id location)
        context-id        (:id context)
        lid               (build-sql-id id)
        point             (geo/make-pg-point location)
        coverage          {:context-id context-id
                           :lid        lid
                           :location   point}
        raster-resolution (get-in context [:options :raster-resolution])
        with-raster?      (some? raster-resolution)
        raster-file       (when with-raster?
                            (raster-file-name id))
        raster-path       (when with-raster?
                            (file-store/full-path (:context-store-path context) raster-file))]
    (cond
      (and (check-coverage-exists? db coverage)
           (or (not with-raster?)
               (file-store/exists? raster-path)))
      {:id id :resolved true :extra :cached}

      (check-outside-region? db coverage)
      {:id id :resolved false :extra :outside-region}

      :else
      (try
        (let [criteria         (get-in context [:options :coverage-criteria])
              polygon          (compute-coverage-polygon service location criteria)
              clipped-polygon  (clip-polygon db context polygon)]
          (when raster-file
            (rasterize/rasterize clipped-polygon
                                 raster-path
                                 {:ref-coords {:lat 0 :lon 0}
                                  :resolution raster-resolution}))
          (upsert-coverage! db (assoc coverage
                                      :coverage clipped-polygon
                                      :raster-file raster-file))
          {:id id :resolved true :extra :computed})
        (catch Exception e
          (warn e "failed to compute coverage" {:location location :context context})
          {:id id :resolved false :extra :failed})))))

(defn- resolve-coverages!
  [{:keys [db file-store] :as service} context-id locations]
  (s/assert ::boundary/locations locations)
  (let [cid     (build-sql-id context-id)
        context (select-context db cid)]
    (when (nil? context)
      (throw (ex-info "Context has not been setup; cannot resolve coverages" {:context-id context-id})))
    (let [context-store-path (file-store/setup-collection file-store :coverages context-id)
          context            (assoc context :context-store-path context-store-path)]
      (doall (map (partial resolve-coverage! service context) locations)))))


;; Service definition ========================================================
;;

(defrecord CoverageService [db file-store runner])

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

  (resolve-coverages! [this context-id locations]
    (resolve-coverages! this context-id locations))

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
                               :distance  20
                               :raster    "/tmp/buffer.tif"}))

  (time
   (boundary/compute-coverage service
                              {:lat -1.2741 :lon 36.7931}
                              {:algorithm    :driving-friction
                               :driving-time 60
                               :raster       "/tmp/nairobi.tif"
                               :ref-coords   {:lat 5.4706946 :lon 33.9126084}
                               :resolution   {:xres 0.0008333 :yres 0.0008333}}))

  (setup-context (dev/coverage) [:project 1]
                 {:region-id 1
                  :raster-resolution {:xres (double 1/400) :yres (double -1/400)}
                  :coverage-criteria {:algorithm :driving-friction :driving-time 120}})

  (destroy-context (dev/coverage) [:project 1])

  (resolve-coverages! (dev/coverage) [:project 1]
                      [{:id [:provider 1] :lat 6.5 :lon 18.27}
                       {:id [:provider 2] :lat 4.95 :lon 15.85}
                       {:id [:provider 3] :lat -4 :lon 18}
                       {:id [:provider 4] :lat 7.37 :lon 15.48}])

  nil)
