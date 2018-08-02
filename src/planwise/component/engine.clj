(ns planwise.component.engine
  (:require [planwise.boundary.engine :as boundary]
            [planwise.boundary.projects2 :as projects2]
            [planwise.boundary.providers-set :as providers-set]
            [planwise.boundary.sources :as sources-set]
            [planwise.component.coverage.greedy-search :refer :all]
            [planwise.boundary.coverage :as coverage]
            [planwise.engine.raster :as raster]
            [planwise.component.coverage.rasterize :as rasterize]
            [clojure.string :refer [join]]
            [planwise.engine.demand :as demand]
            [planwise.util.files :as files]
            [integrant.core :as ig]
            [clojure.core.memoize :as memoize]
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
                                  updated-sources           (map (fn [source] (update-source-if-needed source id-sources-under-coverage provider total-demand)) sources)
                                  updated-provider          (assoc provider :satisfied (min (:capacity provider) total-demand))]
                              {:providers (conj providers updated-provider)
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
  [engine project {:keys [changeset providers-data new-providers-geom] :as scenario}]
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
                          :demand-raster    demand-raster}
    ;; Compute coverage of providers that are not yet computed
        changes-geom    (reduce (fn [changes-geom {:keys [provider-id location] :as change}]
                                  (let [coverage-path (str "data/scenarios/" project-id "/coverage-cache/" (:provider-id change) ".tif")
                                        polygon  (when-not (.exists (io/as-file coverage-path)) (coverage/compute-coverage coverage location (merge criteria {:raster coverage-path})))]
                                    (if polygon
                                      (assoc changes-geom (keyword provider-id) {:coverage-geom (:geom (coverage/as-geojson coverage polygon))})
                                      changes-geom))) {} changeset)]

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
       :providers-data   (into updated-providers updated-changes)
       :new-providers-geom   (merge new-providers-geom changes-geom)})))

(defn sources-under
  [engine set-id provider algorithm filter-options]
  (let [source-set-component (:sources-set engine)
        coverage-component (:coverage engine)]
    (if (:location provider) ; only providers in changeset have location (see function change-to-provider)
      (sources-set/list-sources-under-coverage source-set-component
                                               set-id
                                               (:coverage-geom provider))
      (sources-set/list-sources-under-provider-coverage source-set-component
                                                        set-id
                                                        (:id provider)
                                                        algorithm
                                                        filter-options))))

(defn- change-to-provider
  [{:keys [provider-id location coverage-geom] :as change} coverage-fn]
  (let [change (assoc (select-keys change [:capacity :location :coverage-geom]) :id provider-id)]
    (if coverage-geom
      change
      (assoc change :coverage-geom (coverage-fn location)))))

(defn compute-scenario-by-point
  [engine project {:keys [changeset providers-data sources-data new-providers-geom] :as scenario}]
  (let [algorithm        (:coverage-algorithm project)
        filter-options   (get-in project [:config :coverage :filter-options])
        criteria         (merge {:algorithm (keyword algorithm)} filter-options)
        coverage-fn      (fn [provider] (coverage/compute-coverage (:coverage engine) provider criteria))
        providers        (map #(change-to-provider % coverage-fn) changeset)
        sources          sources-data
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

    (let [updated-sources          (:sources result-step1)
          updated-providers        (map #(dissoc % :coverage-geom) result-step2)
          as-geojson               (fn [geom] {:coverage-geom (:geom (coverage/as-geojson (:coverage engine) geom))})
          changes-geom             (reduce (fn [tree {:keys [id coverage-geom]}] (assoc tree (keyword id) (as-geojson coverage-geom))) {} providers)
          total-sources-demand     (sum-map sources :quantity)
          total-satisfied-demand   (sum-map updated-providers :satisfied)
          total-unsatisfied-demand (sum-map updated-providers :unsatisfied)]

      {:raster-path      nil
       :pending-demand   total-unsatisfied-demand
       :covered-demand   total-satisfied-demand
       :providers-data   updated-providers
       :sources-data     updated-sources
       :new-providers-geom (merge new-providers-geom changes-geom)})))

(defn compute-scenario
  [engine project scenario]
  (let [source-set (sources-set/get-source-set-by-id (:sources-set engine) (:source-set-id project))]
    (if (= (:type source-set) "points")
      (compute-scenario-by-point engine project scenario)
      (compute-scenario-by-raster engine project scenario))))


(defn- get-max-distance
  [coord vector raster]
  (reduce
   (fn [max next] (let [d (euclidean-distance coord (get-geo next raster))]
                    (if (> d max) d max))) 0 vector))

(defn- get-resolution
  [{:keys [geotransform]}]
  (-> geotransform vec second))

(defn coverage-fn
  [coverage-comp {:keys [idx coord res get-avg]} {:keys [data geotransform xsize ysize] :as raster} criteria]
  (let [[lon lat :as coord] (or coord (get-geo idx raster))
        polygon (coverage/compute-coverage coverage-comp {:lat lat :lon lon} criteria)
        coverage (raster/create-raster (rasterize/rasterize polygon {:res res}))
        population-reacheable (demand/count-population-under-coverage raster coverage)]
    (if get-avg
      {:max (get-max-distance coord (demand/get-coverage raster coverage) raster)}
      {:coverage population-reacheable
       :coverage-geom (:geom (coverage/as-geojson coverage-comp polygon))
       :location {:lat lat :lon lon}})))

(defn search-optimal-location
  ([engine project scenario]
   (search-optimal-location engine project scenario (raster/read-raster (str "data/" (:raster scenario) ".tif"))))
  ([{:keys [coverage] :as engine} {:keys [engine-config config provider-set-id coverage-algorithm] :as project} scenario raster]
   (let [algorithm     (keyword coverage-algorithm)
         demand-quartiles (:demand-quartiles engine-config)
         criteria         (assoc (get-in config [:coverage :filter-options]) :algorithm (keyword coverage-algorithm))
         aux-fn          #(coverage-fn coverage % raster criteria)
         cost-fn  (memoize/lu (fn [val {:keys [format get-avg]}] (catch-exc aux-fn  {:get-avg get-avg
                                                                                     format (if (= :idx format) (first val) val)
                                                                                     :res (get-resolution raster)})))
         bounds    (when provider-set-id (providers-set/get-radius-from-computed-coverage (:providers-set engine) criteria provider-set-id))]
     (catch-exc
      greedy-search 20 raster cost-fn demand-quartiles {:bounds bounds :n 100}))))

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
    (compute-scenario engine project scenario))
  (search-optimal-location [engine project scenario]
    (search-optimal-location engine project scenario))
  (search-optimal-location [engine project scenario raster]
    (search-optimal-location engine project scenario raster)))

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

(comment
  ;REPL testing
  ;Correctnes of coverage

  (def raster (raster/read-raster "data/scenarios/44/initial-5903759294895159612.tif"))
  (def criteria {:algorithm :simple-buffer :distance 20})
  (def val 1072404)
  (def f (fn [val] (engine/coverage-fn (:coverage engine) {:idx val} raster criteria)))
  (f val) ;idx:  1072404 | total:  17580.679855613736  |demand:  17580
          ;where total: (total-sum (vec (demand/get-coverage raster coverage)) data)
)

(comment
    ;REPL testing
    ;Timing
        ;Assuming computed providers

  (def projects2 (:planwise.component/projects2 integrant.repl.state/system))
  (def scenarios (:planwise.component/scenarios integrant.repl.state/system))
  (def providers-set (:planwise.component/providers-set integrant.repl.state/system))
  (def coverage (:planwise.component/coverage integrant.repl.state/system))
  (defn new-engine []
    (map->Engine {:providers-set providers-set :coverage coverage}))

  (new-engine)
  (require '[planwise.boundary.scenarios :as scenarios])

    ;Criteria: walking friction
  (def project   (projects2/get-project projects2 51))
  (def scenario (scenarios/get-scenario scenarios 362))
  (time (search-optimal-location (new-engine) project scenario)); "Elapsed time: 30125.428086 msecs"

    ;Criteria: driving friction
  (def project   (projects2/get-project projects2 57))
  (def scenario (scenarios/get-scenario scenarios 399))
  (time (search-optimal-location (new-engine) project scenario));"Elapsed time: 28535.980406 msecs"

    ;Criteria: pg-routing
  (def project   (projects2/get-project projects2 53))
  (def scenario  (scenarios/get-scenario scenarios 380))
  (time (search-optimal-location (new-engine) project scenario)); "Elapsed time: 16058.839293 msecs"

  ;Criteria: simple buffer
  (def project   (projects2/get-project projects2 55))
  (def scenario  (scenarios/get-scenario scenarios 382))
  (time (search-optimal-location (new-engine) project scenario)));"Elapsed time: 36028.555081 msecs"

;Testing over Kilifi
  ;;Efficiency
    ;;Images

(defn generate-raster-sample
  [coverage locations criteria]
  (let [kilifi-pop (raster/read-raster "data/kilifi.tif")
        new-pop    (raster/read-raster "data/cerozing.tif")
        dataset-fn (fn [loc] (let [polygon (coverage/compute-coverage coverage loc criteria)] (rasterize/rasterize polygon {:res 1/1200})))
        get-index  (fn [dataset] (vec (demand/get-coverage kilifi-pop (raster/create-raster dataset))))
        same-values (fn [set] (map (fn [i] [i (aget (:data kilifi-pop) i)]) set))
        set         (reduce into (mapv #(-> % dataset-fn get-index same-values) locations))
        new-data    (reduce (fn [new-data [idx val]] (assoc new-data idx val)) (vec (:data new-pop)) set)
        raster      (raster/update-raster kilifi-pop (float-array new-data))]
    raster))

(defn generate-project
  [raster {:keys [algorithm] :as criteria}]
  (let [demand-quartiles (vec (demand/compute-population-quartiles raster))
        provider-set-id {:walking-friction 5 :pgrouting-alpha 7
                         :driving-friction 8 :simple-buffer 4}]
    {:bbox '[(-3.9910888671875 40.2415275573733) (-2.3092041015625 39.0872802734376)]
     :region-id 85, :config {:coverage {:filter-options (dissoc criteria :algorithm)}}
     :provider-set-id (algorithm provider-set-id) :source-set-id 2 :owner-id 1
     :engine-config {:demand-quartiles demand-quartiles
                     :source-demand 1311728}
     :coverage-algorithm (name algorithm)}))

(comment
;;For visualizing effectiveness
  (def criteria {:algorithm :walking-friction, :walking-time 120})
  (def criteria {:algorithm :pgrouting-alpha :driving-time 60})
  (def criteria {:algorithm :driving-friction :driving-time 90})

 ;Test 0
  (def locations0 [{:lon 39.672257821715334, :lat -3.8315073359981278}])
  (def raster-test0 (generate-raster-sample coverage locations criteria))

  ;Test 1
  (def locations1 [{:lon 39.863 :lat -3.097} {:lon 39.672257821715334, :lat -3.8315073359981278}])
  (def raster-test1 (generate-raster-sample coverage locations1 criteria))

  ;Test 2
  (def locations2 [{:lon 39.672257821715334, :lat -3.8315073359981278} {:lon 39.863 :lat -3.097} {:lon 39.602 :lat -3.830}])
  (def raster-test2 (generate-raster-sample coverage locations2 criteria))

  ;Test 3
  (def locations3 [{:lon 39.672257821715334, :lat -3.8315073359981278} {:lon 39.863 :lat -3.097} {:lon 39.479 :lat -3.407}])
  (def raster-test3 (generate-raster-sample coverage locations3 criteria))

  (def project-test (generate-project raster-test criteria))
  (search-optimal-location engine project-test {} raster-test))