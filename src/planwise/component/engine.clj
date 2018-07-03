(ns planwise.component.engine
  (:require [planwise.boundary.engine :as boundary]
            [planwise.boundary.projects2 :as projects2]
            [planwise.boundary.providers-set :as providers-set]
            [planwise.boundary.sources :as sources-set]
            [planwise.boundary.coverage :as coverage]
            [planwise.engine.raster :as raster]
            [planwise.component.coverage.rasterize :as rasterize]
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
         (map #(select-keys % [:id :name :capacity :raster]))
         (sort-by :capacity)
         reverse)))

(defn- compute-provider
  [props provider]
  (let [{:keys [id provider-id action raster capacity]} provider
        {:keys [update? demand-raster project-capacity project-id provider-set-id]} props
        path (if action
               (str "data/scenarios/" project-id "/coverage-cache/" provider-id ".tif")
               (str "data/coverage/" provider-set-id "/" raster ".tif"))
        coverage-raster (raster/read-raster path)
        scaled-capacity (* capacity project-capacity)
        population-reachable (demand/count-population-under-coverage demand-raster coverage-raster)]

    (if update?
      {:unsatisfied population-reachable}
      (do
        (debug "Subtracting" scaled-capacity "of provider" (or provider-id id) "reaching" population-reachable "people")
        (when-not (zero? population-reachable)
          (let [factor (- 1 (min 1 (/ scaled-capacity population-reachable)))]
            (demand/multiply-population-under-coverage! demand-raster coverage-raster (float factor))))
        {:id         (or id provider-id)
         :capacity   capacity
         :satisfied  (min capacity population-reachable)}))))

(defn- compute-providers-demand
  [set props]
  (first
   (reduce
    (fn [[processed-providers props] provider]
      [(conj processed-providers (compute-provider props provider)) props])
    [[] props] set)))

(defn compute-initial-scenario-by-raster
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
                          :demand-raster    demand-raster}]
    (debug "Source population demand:" source-demand)
    (let [processed-providers (compute-providers-demand providers props)
          scenario-demand      (demand/count-population demand-raster)
          quartiles           (vec (demand/compute-population-quartiles demand-raster))
          update-providers    (compute-providers-demand providers (assoc props :update? true))]
      (raster/write-raster demand-raster (str "data/" raster-path ".tif"))
      (raster/write-raster (demand/build-renderable-population demand-raster quartiles) (str "data/" raster-path ".map.tif"))
      {:raster-path      raster-path
       :source-demand    source-demand
       :pending-demand   scenario-demand
       :covered-demand   (- source-demand scenario-demand)
       :demand-quartiles quartiles
       :providers-data   (mapv (fn [[a b]] (merge a b)) (map vector processed-providers update-providers))})))

(defn sum-map
  [coll f]
  (reduce + (map f coll))) ;another way: (apply + (map f coll))

(defn update-source
  [source provider total-demand]
  (let [ratio (float (/ (:quantity source) total-demand))
        unsatisfied (double (max 0 (- (:quantity source) (* (:capacity provider) ratio))))
        updated-source (assoc source :quantity unsatisfied)]
    updated-source))

(defn need-to-update-source?
  [source ids]
  (and (ids (:id source))
       (> (:quantity source) 0)))

(defn update-source-if-needed
  [source ids provider total-demand]
  (if (need-to-update-source? source ids)
    (update-source source provider total-demand)
    source))

(defn compute-initial-scenario-by-point
  [engine project]
  (let [provider-set-id  (:provider-set-id project)
        providers        (project-providers engine project) ;sort by capacity
        sources          (sources-set/list-sources-in-set (:sources-set engine) (:source-set-id project))
        algorithm        (:coverage-algorithm project)
        filter-options   (get-in project [:config :coverage :filter-options])
        fn-sources-under (fn [provider] (sources-set/list-sources-under-provider-coverage (:sources-set engine) (:source-set-id project) (:id provider) algorithm filter-options))
        fn-select-by-id  (fn [sources ids] (filter (fn [source] (ids (:id source))) sources))
        result-step1     (reduce ; over providers
                          (fn [computed-state provider]
                            (let [providers                 (:providers computed-state)
                                  sources                   (:sources computed-state)
                                  id-sources-under-coverage (set (map :id (fn-sources-under provider)))         ; create set with sources' id
                                  sources-under-coverage    (fn-select-by-id sources id-sources-under-coverage) ; updated sources under coverage
                                  total-demand              (sum-map sources-under-coverage :quantity)        ; total demand requested to current provider
                                  updated-sources           (map (fn [source] (update-source-if-needed source id-sources-under-coverage provider total-demand)) sources)]
                              {:providers (conj providers (assoc provider :satisfied (min (:capacity provider) total-demand)))
                               :sources updated-sources}))
                          {:providers nil
                           :sources sources}
                          providers)
        result-step2     (map (fn [provider]  ; resolve unsatisfied demand per provider
                                (let [sources                   (:sources result-step1)
                                      id-sources-under-coverage (set (map :id (fn-sources-under provider)))
                                      sources-under-coverage    (fn-select-by-id sources id-sources-under-coverage) ; updated sources under coverage
                                      total-demand              (sum-map sources-under-coverage :quantity)]
                                  (assoc provider :unsatisfied total-demand)))
                              (:providers result-step1))]

    (let [updated-sources           (map #(select-keys % [:id :quantity]) (:sources result-step1)) ; persist only id and quantity
          updated-providers         result-step2
          total-sources-demand      (sum-map sources :quantity)
          total-satisfied-demand    (sum-map updated-providers :satisfied)
          total-unsatisfied-demand  (sum-map updated-providers :unsatisfied)]

      {:raster-path       nil
       :source-demand     total-sources-demand
       :pending-demand    total-unsatisfied-demand
       :covered-demand    total-satisfied-demand
       :demand-quartiles  nil
       :providers-data    updated-providers
       :sources-data      updated-sources})))

(defn compute-initial-scenario
  [engine project]
  (let [source-set (sources-set/get-source-set-by-id (:sources-set engine) (:source-set-id project))]
    (if (= (:type source-set) "points")
      (compute-initial-scenario-by-point engine project)
      (compute-initial-scenario-by-raster engine project))))

(defn compute-scenario-by-raster
  [engine project {:keys [changeset providers-data] :as scenario}]
  (let [coverage        (:coverage engine)
        providers       (project-providers engine project)
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

    ;; Compute demand from initial scenario
    ;; TODO refactor with initial-scenario loop
    (let [processed-changes         (compute-providers-demand changeset props)
          pending-demand            (demand/count-population demand-raster)
          initial-providers-data    (mapv #(dissoc % :unsatisfied) providers-data)
          update-changes    (compute-providers-demand changeset (assoc props :update? true))
          update-providers  (compute-providers-demand providers (assoc props :update? true))
          updated-providers (mapv (fn [[a b]] (merge a b)) (map vector initial-providers-data update-providers))
          updated-changes   (mapv (fn [[a b]] (merge a b)) (map vector processed-changes update-changes))]
      (raster/write-raster demand-raster (str "data/" raster-path ".tif"))
      (raster/write-raster (demand/build-renderable-population demand-raster quartiles) (str "data/" raster-path ".map.tif"))
      {:raster-path      raster-path
       :pending-demand   pending-demand
       :covered-demand   (- source-demand pending-demand)
       :providers-data   (into updated-providers updated-changes)})))

(defn sources-under
  [engine set-id provider algorithm filter-options]
  (let [source-set-component (:sources-set engine)
        coverage-component (:coverage engine)]
    (if (:location provider)
      (let [criteria (merge {:algorithm (keyword algorithm)} filter-options)
            geom     (coverage/compute-coverage coverage-component
                                                {:lat (get-in provider [:location :lat])
                                                 :lon (get-in provider [:location :lon])}
                                                criteria)]
        (sources-set/list-sources-under-coverage source-set-component
                                                 set-id
                                                 geom))
      (sources-set/list-sources-under-provider-coverage source-set-component
                                                        set-id
                                                        (:id provider)
                                                        algorithm
                                                        filter-options))))

(defn- change-to-provider
  [change]
  {:id (:provider-id change)
   :capacity (:capacity change)
   :location (:location change)})

(defn compute-scenario-by-point
  [engine project {:keys [changeset providers-data sources-data] :as scenario}]
  (let [providers        (map change-to-provider changeset)
        sources          sources-data
        algorithm        (:coverage-algorithm project)
        filter-options   (get-in project [:config :coverage :filter-options])
        fn-sources-under (fn [provider] (sources-under engine (:source-set-id project) provider algorithm filter-options))
        fn-filter-by-id  (fn [sources ids] (filter (fn [source] (ids (:id source))) sources))
        result-step1     (reduce ; over providers
                          (fn [computed-state provider]
                            (let [providers                 (:providers computed-state)
                                  sources                   (:sources computed-state)
                                  id-sources-under-coverage (set (map :id (fn-sources-under provider)))         ; create set with sources' id
                                  sources-under-coverage    (fn-filter-by-id sources id-sources-under-coverage) ; take only the sources under coverage (using the id to filter)
                                  total-demand              (sum-map sources-under-coverage :quantity)          ; total demand requested to current provider
                                  updated-sources           (map (fn [source] (update-source-if-needed source id-sources-under-coverage provider total-demand)) sources)]
                              {:providers (conj providers (assoc provider :satisfied (min (:capacity provider) total-demand)))
                               :sources updated-sources}))
                          {:providers nil
                           :sources sources}
                          providers)
        result-step2     (map (fn [provider]  ; resolve unsatisfied demand per provider (for all providers!)
                                (let [sources                   (:sources result-step1)
                                      id-sources-under-coverage (set (map :id (fn-sources-under provider)))
                                      sources-under-coverage    (fn-filter-by-id sources id-sources-under-coverage) ; updated sources under coverage
                                      total-demand              (sum-map sources-under-coverage :quantity)]
                                  (assoc provider :unsatisfied total-demand)))
                              (concat providers-data (:providers result-step1)))]

    (let [updated-sources           (:sources result-step1)
          updated-providers         result-step2
          total-sources-demand      (sum-map sources :quantity)
          total-satisfied-demand    (sum-map updated-providers :satisfied)
          total-unsatisfied-demand  (sum-map updated-providers :unsatisfied)]

      {:raster-path      nil
       :pending-demand   total-unsatisfied-demand
       :covered-demand   total-satisfied-demand
       :providers-data   updated-providers
       :sources-data     updated-sources})))

(defn compute-scenario
  [engine project scenario]
  (let [source-set (sources-set/get-source-set-by-id (:sources-set engine) (:source-set-id project))]
    (if (= (:type source-set) "points")
      (compute-scenario-by-point engine project scenario)
      (compute-scenario-by-raster engine project scenario))))

;New provider

(defn pixel->coord
  [geotransform pixels-vec]
  (let [[x0 _ _ y0 _ _] (vec geotransform)
        coord-fn  (fn [[x y]] [(+ x0 (/ x 1200)) (+ y0 (/ y (- 1200)))])]
    (coord-fn pixels-vec)))

(defn get-pixel
  [idx xsize ysize]
  (let [height (quot idx xsize)
        width (mod idx xsize)]
    [width height]))

(defn unique-random-numbers [n bound]
  (loop [num-set (set (take n (repeatedly #(rand-int bound))))]
    (let [size  (count num-set)]
      (if (= size n)
        num-set
        (recur (clojure.set/union num-set  (set (take (- n size) (repeatedly #(rand-int n))))))))))

(defn coverage-fn
  [coverage idx {:keys [data geotransform xsize ysize] :as raster} criteria]
  (let [[lon lat] (pixel->coord geotransform (get-pixel idx xsize ysize))
        polygon (coverage/compute-coverage coverage {:lat lat :lon lon} criteria)
        coverage (raster/create-raster (rasterize/rasterize polygon))]
    (demand/count-population-under-coverage raster coverage)))

;REPL testing of coverage
(comment
  (def val (rand-nth (map-indexed vector (vec (:data raster)))))
  (require '[planwise.engine.raster :as raster])
  (require '[planwise.component.engine :as engine])
  (def engine (:planwise.component/engine system))
  (def raster (raster/read-raster "data/scenarios/44/initial-5903759294895159612.tif"))
  (def criteria {:algorithm :simple-buffer :distance 20})
  (def vals (vec (engine/unique-random-numbers 15 (alength (:data raster)))))
  (def f (fn [val] (engine/coverage-fn (:coverage engine) val raster criteria)))
  (map f vals))




(defn clear-project-cache
  [this project-id]
  (let [scenarios-path (str "data/scenarios/" project-id)]
    (files/delete-files-recursively scenarios-path true)))

(defrecord Engine [providers-set sources-set coverage]
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
