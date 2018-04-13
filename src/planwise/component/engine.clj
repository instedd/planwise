(ns planwise.component.engine
  (:require [planwise.boundary.engine :as boundary]
            [planwise.boundary.projects2 :as projects2]
            [planwise.boundary.datasets2 :as datasets2]
            [planwise.boundary.coverage :as coverage]
            [planwise.engine.raster :as raster]
            [planwise.engine.demand :as demand]
            [planwise.util.files :as files]
            [integrant.core :as ig]
            [clojure.java.io :as io]
            [taoensso.timbre :as timbre]))

(timbre/refer-timbre)

;; Computing a scenario:
;; - compute the initial scenario or retrieve a cached version
;; - apply the changeset actions in order

;; Computing the initial scenario:
;; - retrieve the base dataset for the project region
;; - scale/filter population for the given project parameters (demographics
;;   filters, target population, etc)
;; - retrieve the sites in the region, filtering by project parameters and order
;;   by descending capacity
;; - subtract the capacity of each site from the running unsatisfied demand

(defn- project-base-demand
  [project]
  (let [source-id              (:population-source-id project)
        region-id              (:region-id project)
        project-config         (:config project)
        population-raster-file (str "data/populations/data/" source-id "/" region-id ".tif")
        raster                 (raster/read-raster population-raster-file)
        target-factor          (/ (get-in project-config [:demographics :target]) 100)]
    ;; scale raster demand according to project's target
    (doto raster
      (demand/multiply-population! (float target-factor)))))

(defn- project-sites
  [{:keys [datasets2]} {:keys [dataset-id dataset-version region-id coverage-algorithm config]}]
  (let [version          (or dataset-version (:last-version (datasets2/get-dataset datasets2 dataset-id)))
        coverage-options (get-in config [:coverage :filter-options])
        filter-options   {:region-id          region-id
                          :coverage-algorithm coverage-algorithm
                          :coverage-options   coverage-options}
        sites            (datasets2/get-sites-with-coverage-in-region datasets2 dataset-id version filter-options)]
    (->> sites
         (map #(select-keys % [:id :capacity :raster]))
         (sort-by :capacity)
         reverse)))

(defn compute-initial-scenario
  [engine project]
  (let [demand-raster  (project-base-demand project)
        sites          (project-sites engine project)
        dataset-id     (:dataset-id project)
        project-id     (:id project)
        project-config (:config project)
        capacity       (get-in project-config [:sites :capacity] 1)
        source-demand  (demand/count-population demand-raster)
        raster-path    (str "scenarios/" project-id "/initial")]
    (debug "Source population demand:" source-demand)
    (dorun (for [site sites]
             (let [capacity             (* capacity (:capacity site))
                   coverage-name        (:raster site)
                   coverage-path        (str "data/coverage/" dataset-id "/" coverage-name ".tif")
                   coverage-raster      (raster/read-raster coverage-path)
                   population-reachable (demand/count-population-under-coverage demand-raster coverage-raster)]
               (debug "Subtracting" capacity "of site" (:id site) "reaching" population-reachable "people")
               (when-not (zero? population-reachable)
                 (let [factor (- 1 (min 1 (/ capacity population-reachable)))]
                   (demand/multiply-population-under-coverage! demand-raster coverage-raster (float factor)))))))
    (let [initial-demand (demand/count-population demand-raster)
          quartiles      (vec (demand/compute-population-quartiles demand-raster))]
      (io/make-parents (io/as-file (str "data/" raster-path)))
      (raster/write-raster demand-raster (str "data/" raster-path ".tif"))
      (raster/write-raster (demand/build-renderable-population demand-raster quartiles) (str "data/" raster-path ".map.tif"))
      {:raster-path      raster-path
       :source-demand    source-demand
       :pending-demand   initial-demand
       :covered-demand   (- source-demand initial-demand)
       :demand-quartiles quartiles})))

(defn compute-scenario
  [engine project scenario]
  (let [coverage  (:coverage engine)
        algorithm (keyword (:coverage-algorithm project))
        filter-options (get-in project [:config :coverage :filter-options])
        criteria (merge {:algorithm algorithm} filter-options)]
    (doseq [change (:changeset scenario)]
      ;; TODO use cache if exist
      (let [lat (get-in change [:location :lat])
            lon (get-in change [:location :lon])
            raster (str "data/scenarios/coverage-cache/" (:site-id change) ".tiff")]
        (coverage/compute-coverage coverage {:lat lat :lon lon} (merge criteria {:raster raster}))))))

(defn clear-project-cache
  [this project-id]
  (let [scenarios-path (str "data/scenarios/" project-id)]
    (files/delete-files-recursively scenarios-path)))

(defn compute-scenario
  [scenario]
  scenario)

(defrecord Engine [datasets2 coverage]
  boundary/Engine
  (compute-initial-scenario [engine project]
    (compute-initial-scenario engine project))
  (clear-project-cache [engine project]
    (clear-project-cache engine project))
  (compute-scenario [engine project scenario]
    (compute-scenario engine project scenario)))

(defmethod ig/init-key :planwise.component/engine
  [_ config]
  (map->Engine config))

(comment
  ;; REPL testing

  (def projects2 (:planwise.component/projects2 integrant.repl.state/system))
  (def datasets2 (:planwise.component/datasets2 integrant.repl.state/system))

  (defn new-engine []
    (map->Engine {:datasets2 datasets2}))

  (projects2/get-project projects2 5)

  (new-engine)

  (project-sites (new-engine) (projects2/get-project projects2 5))

  (compute-initial-scenario (new-engine) (projects2/get-project projects2 5)))
