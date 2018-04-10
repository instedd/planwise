(ns planwise.component.engine
  (:require [planwise.boundary.engine :as boundary]
            [planwise.boundary.projects2 :as projects2]
            [integrant.core :as ig]
            [planwise.engine.raster :as raster]
            [planwise.engine.demand :as demand]))

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
  [project]
  [])

(defn compute-initial-scenario
  [engine project]
  (let [demand-raster (project-base-demand project)]
    demand-raster))

(defn compute-scenario
  [scenario]
  scenario)

(defrecord Engine [projects2]
  boundary/Engine
  (compute-initial-scenario [engine project]
    (compute-initial-scenario engine project)))

(defmethod ig/init-key :planwise.component/engine
  [_ config]
  (map->Engine config))

;; REPL testing
(comment
  (def projects2 (:planwise.component/projects2 integrant.repl.state/system))

  (defn new-engine []
    (map->Engine {:projects2 projects2}))

  (projects2/get-project projects2 5)

  (new-engine)

  (-> (compute-initial-scenario (new-engine) (projects2/get-project projects2 5))
      (demand/count-population)))
