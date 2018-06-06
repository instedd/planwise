(ns planwise.component.engine
  (:require [planwise.boundary.engine :as boundary]
            [planwise.boundary.projects2 :as projects2]
            [planwise.boundary.providers-set :as providers-set]
            [planwise.boundary.coverage :as coverage]
            [planwise.engine.raster :as raster]
            [clojure.string :refer [join]]
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
;; - retrieve the base provider-set for the project region
;; - scale/filter population for the given project parameters (demographics
;;   filters, target population, etc)
;; - retrieve the providers in the region, filtering by project parameters and order
;;   by descending capacity
;; - subtract the capacity of each provider from the running unsatisfied demand

(defn- project-base-demand
  [project]
  (let [source-id              (:source-set-id project)
        region-id              (:region-id project)
        project-config         (:config project)
        population-raster-file (str "data/populations/data/" source-id "/" region-id ".tif")
        raster                 (raster/read-raster population-raster-file)
        target-factor          (/ (get-in project-config [:demographics :target]) 100)]
    ;; scale raster demand according to project's target
    (doto raster
      (demand/multiply-population! (float target-factor)))))

(defn- project-providers
  [{:keys [providers-set]} {:keys [provider-set-id provider-set-version region-id coverage-algorithm config]}]
  (let [version          (or provider-set-version (:last-version (providers-set/get-provider-set providers-set provider-set-id)))
        coverage-options (get-in config [:coverage :filter-options])
        tags             (get-in config [:providers :tags])
        filter-options   {:region-id          region-id
                          :coverage-algorithm coverage-algorithm
                          :coverage-options   coverage-options
                          :tags tags}
        providers         (providers-set/get-providers-with-coverage-in-region providers-set provider-set-id version filter-options)]
    (->> providers
         (map #(select-keys % [:id :capacity :raster]))
         (sort-by :capacity)
         reverse)))

(defn compute-initial-scenario
  [engine project]
  (let [demand-raster    (project-base-demand project)
        providers        (project-providers engine project)
        provider-set-id  (:provider-set-id project)
        project-id       (:id project)
        project-config   (:config project)
        capacity         (get-in project-config [:providers :capacity])
        source-demand    (demand/count-population demand-raster)
        raster-full-path (files/create-temp-file (str "data/scenarios/" project-id) "initial-" ".tif")
        raster-path      (get (re-find (re-pattern "^data/(.*)\\.tif$") raster-full-path) 1)
        props            {:project-capacity capacity
                          :provider-set-id  provider-set-id
                          :project-id       project-id
                          :demand-raster    demand-raster}
        providers-data   (demand/compute-providers-demand providers props)]
    (debug "Source population demand:" source-demand)
    (dorun (for [provider providers]
             (let [capacity             (* capacity (:capacity provider))
                   coverage-name        (:raster provider)
                   coverage-path        (str "data/coverage/" provider-set-id "/" coverage-name ".tif")
                   coverage-raster      (raster/read-raster coverage-path)
                   population-reachable (demand/count-population-under-coverage demand-raster coverage-raster)]
               (debug "Subtracting" capacity "of provider" (:id provider) "reaching" population-reachable "people")
               (when-not (zero? population-reachable)
                 (let [factor (- 1 (min 1 (/ capacity population-reachable)))]
                   (demand/multiply-population-under-coverage! demand-raster coverage-raster (float factor)))))))

    (let [initial-demand   (demand/count-population demand-raster)
          quartiles        (vec (demand/compute-population-quartiles demand-raster))
          update-providers (demand/compute-providers-demand providers (merge props {:update? true}))]
      (raster/write-raster demand-raster (str "data/" raster-path ".tif"))
      (raster/write-raster (demand/build-renderable-population demand-raster quartiles) (str "data/" raster-path ".map.tif"))
      {:raster-path      raster-path
       :source-demand    source-demand
       :pending-demand   initial-demand
       :covered-demand   (- source-demand initial-demand)
       :demand-quartiles quartiles
       :providers-data   (mapv (fn [[a b]] (merge a b)) (map vector providers-data update-providers))})))

(defn compute-scenario
  [engine project {:keys [changeset providers-data] :as scenario}]
  (let [coverage        (:coverage engine)
        project-id      (:id project)
        project-config  (:config project)
        provider-set-id (:provider-set-id project)
        scenario-id     (:id scenario)
        algorithm       (keyword (:coverage-algorithm project))
        filter-options  (get-in project [:config :coverage :filter-options])
        criteria        (merge {:algorithm algorithm} filter-options)
        capacity        (get-in project-config [:providers :capacity])
        quartiles       (get-in project [:engine-config :demand-quartiles])
        source-demand   (get-in project [:engine-config :source-demand])
        ;; demand-raster starts with the initial-pending-demand
        demand-raster    (raster/read-raster (str "data/" (get-in project [:engine-config :pending-demand-raster-path]) ".tif"))
        raster-full-path (files/create-temp-file (str "data/scenarios/" project-id) (format "%03d-" scenario-id) ".tif")
        raster-path      (get (re-find (re-pattern "^data/(.*)\\.tif$") raster-full-path) 1)
        props            {:project-capacity capacity
                          :provider-set-id  provider-set-id
                          :project-id       project-id
                          :demand-raster    demand-raster}]
    ;; Compute coverage of providers that are not yet computed
    (doseq [change changeset]
      (let [lat      (get-in change [:location :lat])
            lon      (get-in change [:location :lon])
            coverage-path (str "data/scenarios/" project-id "/coverage-cache/" (:provider-id change) ".tif")]
        (if (not (.exists (io/as-file coverage-path)))
          (coverage/compute-coverage coverage {:lat lat :lon lon} (merge criteria {:raster coverage-path})))))
    (let [changes-data (demand/compute-providers-demand changeset props)]
    ;; Compute demand from initial scenario
    ;; TODO refactor with initial-scenario loop
      (dorun (for [change changeset]
               (let [capacity             (* capacity (:capacity change))
                     coverage-path        (str "data/scenarios/" project-id "/coverage-cache/" (:provider-id change) ".tif")
                     coverage-raster      (raster/read-raster coverage-path)
                     population-reachable (demand/count-population-under-coverage demand-raster coverage-raster)]
                 (debug "Subtracting" capacity "of provider" (:provider-id change) "reaching" population-reachable "people")
                 (when-not (zero? population-reachable)
                   (let [factor (- 1 (min 1 (/ capacity population-reachable)))]
                     (demand/multiply-population-under-coverage! demand-raster coverage-raster (float factor)))))))

      (let [pending-demand   (demand/count-population demand-raster)
            update-changes   (demand/compute-providers-demand changeset (assoc props :update? true))
            updated-changes  (mapv (fn [[a b]] (merge a b)) (map vector changes-data update-changes))]
        (raster/write-raster demand-raster (str "data/" raster-path ".tif"))
        (raster/write-raster (demand/build-renderable-population demand-raster quartiles) (str "data/" raster-path ".map.tif"))
        {:raster-path      raster-path
         :pending-demand   pending-demand
         :covered-demand   (- source-demand pending-demand)
         :providers-data   (into providers-data updated-changes)}))))

(defn clear-project-cache
  [this project-id]
  (let [scenarios-path (str "data/scenarios/" project-id)]
    (files/delete-files-recursively scenarios-path true)))

(defrecord Engine [providers-set coverage]
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
  (def scenarios (:planwise.component/scenarios integrant.repl.state/system))
  (def providers-set (:planwise.component/providers-set integrant.repl.state/system))
  (def coverage (:planwise.component/coverage integrant.repl.state/system))

  (defn new-engine []
    (map->Engine {:providers-set providers-set :coverage coverage}))

  (projects2/get-project projects2 5)

  (new-engine)

  (project-providers (new-engine) (projects2/get-project projects2 5))

  (compute-initial-scenario (new-engine) (projects2/get-project projects2 5))
  (compute-scenario (new-engine) (projects2/get-project projects2 23) (planwise.boundary.scenarios/get-scenario scenarios 30))

  nil)
