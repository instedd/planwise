(ns planwise.component.engine
  (:require [planwise.boundary.engine :as boundary]
            [planwise.boundary.projects2 :as projects2]
            [planwise.boundary.providers-set :as providers-set]
            [planwise.boundary.sources :as sources-set]
            [planwise.boundary.coverage :as coverage]
            [planwise.engine.raster :as raster]
            [planwise.component.coverage.rasterize :as rasterize]
            [planwise.engine.suggestions :as suggestions]
            [planwise.engine.demand :as demand]
            [planwise.util.files :as files]
            [planwise.util.collections :refer [sum-by merge-collections-by]]
            [integrant.core :as ig]
            [clojure.java.io :as io]
            [taoensso.timbre :as timbre]))

(timbre/refer-timbre)

;; PATH HELPERS
;; -------------------------------------------------------------------------------------------------

(defn source-raster-data-path
  "Full path to the source raster clipped to a region."
  [source-id region-id]
  (str "data/populations/data/" source-id "/" region-id ".tif"))

(defn provider-coverage-raster-path
  "Full path to the raster coverage for a provider set, given the raster property of a provider."
  [provider-set-id raster]
  (str "data/coverage/" provider-set-id "/" raster ".tif"))

(defn new-provider-coverage-raster-path
  "Full path to the cached coverage of a provider from a create-provider scenario action."
  [project-id id]
  (str "data/scenarios/" project-id "/coverage-cache/" id ".tif"))

(defn scenario-raster-data-path
  "Full path to the data raster for a computed scenario."
  [project-id scenario-filename]
  (str "data/scenarios/" project-id "/" scenario-filename ".tif"))

(defn scenario-raster-map-path
  "Full path to the renderable raster for a computed scenario."
  [project-id scenario-filename]
  (str "data/scenarios/" project-id "/" scenario-filename ".map.tif"))

(defn scenario-raster-path
  "For persisting in the scenarios table"
  [project-id scenario-filename]
  (str "scenarios/" project-id "/" scenario-filename))

(defn scenario-raster-full-path
  "Given a raser path from scenario-raster-path, return the full path to the raster file."
  [raster-path]
  (str "data/" raster-path ".tif"))

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

;; PROVIDERS & COVERAGE RESOLUTION
;; -------------------------------------------------------------------------------------------------

(defn- filter-options-for-project
  "Filter options from project for usage in providers-set/get-providers-coverage-in-region."
  [project]
  (let [region-id          (:region-id project)
        coverage-algorithm (:coverage-algorithm project)
        project-config     (:config project)
        coverage-options   (get-in project-config [:coverage :filter-options])
        tags               (get-in project-config [:providers :tags])]
    {:region-id          region-id
     :coverage-algorithm coverage-algorithm
     :coverage-options   coverage-options
     :tags               tags}))

(defn- coverage-criteria-for-project
  "Criteria options from project for usage in coverage/compute-coverage."
  [project]
  (let [coverage-algorithm (keyword (:coverage-algorithm project))
        project-config     (:config project)
        coverage-options   (get-in project-config [:coverage :filter-options])]
    (assoc coverage-options :algorithm coverage-algorithm)))

(defn- provider-mapper
  "Returns a mapper function for providers into the shape required for computing scenarios."
  [provider-set-id applicable]
  (fn [{:keys [id name capacity raster]}]
    (let [coverage-raster-path (provider-coverage-raster-path provider-set-id raster)]
      {:id                   id
       :name                 name
       :capacity             capacity
       :applicable?          applicable
       :coverage-raster-path coverage-raster-path})))

;; TODO: It would be really nice if we could compute provider's coverage on
;; demand here instead of ahead of time when importing the dataset
;; TODO: in fact, this should return the providers filtered by region, without
;; the raster coverage information; that should be added later if needed (by the
;; project config), or compute the point sources under it
(defn providers-in-project
  "Fetches all providers in the project's region and builds a collection with an
  extra attribute indicating if they apply (wrt. tag filtering). Returned
  providers have :id, :name, :capacity, :applicable and :coverage-raster-path."
  [providers-component project]
  (let [provider-set-id (:provider-set-id project)
        provider-set    (providers-set/get-provider-set providers-component provider-set-id)
        version         (or (:provider-set-version project)
                            (:last-version (providers-set/get-provider-set providers-component provider-set-id)))
        filter-options  (filter-options-for-project project)
        all-providers   (providers-set/get-providers-with-coverage-in-region providers-component
                                                                             provider-set-id
                                                                             version
                                                                             filter-options)
        applicable      (map (provider-mapper provider-set-id true) (:providers all-providers))
        non-applicable  (map (provider-mapper provider-set-id false) (:disabled-providers all-providers))
        providers       (concat applicable non-applicable)]
    (->> providers
         (sort-by :capacity)
         reverse)))

(defn coverage-for-new-provider
  "If the coverage raster file for the given new provider id doesn't exist or
  the polygon is not present in the cache, compute the coverage raster and
  return the coverage polygon clipped to the region of the project."
  ([coverage project change]
   (coverage-for-new-provider coverage project change {}))
  ([coverage project {:keys [id location] :as change} geom-cache]
   (let [project-id  (:id project)
         raster-path (new-provider-coverage-raster-path project-id id)
         cached-geom (get geom-cache id)]
     (if (and (some? cached-geom) (.exists (io/as-file raster-path)))
       ;; geometry already computed and raster file exists
       {:geom        cached-geom
        :raster-path raster-path}
       ;; either geometry or raster file don't exist
       (try
         (io/delete-file raster-path true)
         (let [criteria     (coverage-criteria-for-project project)
               geom         (coverage/compute-coverage coverage location (assoc criteria :raster raster-path))
               clipped-geom (:geom (coverage/geometry-intersected-with-project-region coverage geom (:region-id project)))]
           {:geom        clipped-geom
            :raster-path raster-path})
         (catch Exception e
           (throw (ex-info "New provider failed at computation"
                           (assoc (ex-data e) :id id)
                           e))))))))

(defn resolve-coverages
  [coverage project changeset {:keys [initial-providers scenario-geom-cache]}]
  (let [providers-index (group-by :id initial-providers)]
    (reduce (fn [{:keys [geom-cache changeset] :as accum} {:keys [id] :as change}]
              (case (:action change)
                "create-provider"
                (let [result  (coverage-for-new-provider coverage project change geom-cache)
                      change' (assoc change :coverage-raster-path (:raster-path result))]
                  {:geom-cache (assoc geom-cache id (:geom result))
                   :changeset  (conj changeset change')})

                ("upgrade-provider" "increase-provider")
                (let [initial-provider     (first (get providers-index id))
                      coverage-raster-path (:coverage-raster-path initial-provider)
                      change'              (assoc change :coverage-raster-path coverage-raster-path)]
                  {:geom-cache geom-cache
                   :changeset  (conj changeset change')})

                accum))

            {:geom-cache scenario-geom-cache
             :changeset []}
            changeset)))

(defn merge-provider
  "Merge two or more provider-like maps, but sum their capacity related fields
  and the satisfied demand."
  [& providers]
  (-> (apply merge providers)
      (assoc :capacity (apply sum-by :capacity providers)
             :satisfied-demand (apply sum-by :satisfied-demand providers)
             :used-capacity (apply sum-by :used-capacity providers)
             :free-capacity (apply sum-by :free-capacity providers))))

(defn merge-providers
  "Merge providers by id, but perform addition for the fields :capacity,
  :satisfied-demand, :used-capacity and :free-capacity."
  [& colls]
  (apply merge-collections-by :id merge-provider colls))

;; RASTER SCENARIOS
;; -------------------------------------------------------------------------------------------------

(defn project-base-demand-raster
  "Returns a mutable raster with the initial source demand for the project."
  [project]
  (let [source-id          (:source-set-id project)
        region-id          (:region-id project)
        project-config     (:config project)
        source-raster-file (source-raster-data-path source-id region-id)
        raster             (raster/read-raster source-raster-file)
        target-factor      (/ (get-in project-config [:demographics :target]) 100)]
    ;; scale raster demand according to project's target
    (doto raster
      (demand/multiply-population! (float target-factor)))))

(defn raster-measure-provider
  "Measures the unsatisfied demand and computes the required (extra) capacity
  for a provider in the demand raster."
  [demand-raster capacity-multiplier provider]
  (let [coverage-raster  (raster/read-raster (:coverage-raster-path provider))
        reachable-demand (demand/count-population-under-coverage demand-raster coverage-raster)]
    {:id                 (:id provider)
     :unsatisfied-demand reachable-demand
     :required-capacity  (/ reachable-demand capacity-multiplier)}))

(defn raster-apply-provider!
  "Mutates demand-raster by subtracting the capacity of the provider distributed
  uniformily across the coverage, and returns the satisfied demand for this
  provider as well as the used and remaining capacity."
  [demand-raster capacity-multiplier provider]
  (let [coverage-raster  (raster/read-raster (:coverage-raster-path provider))
        capacity         (:capacity provider)
        scaled-capacity  (* capacity capacity-multiplier)
        reachable-demand (demand/count-population-under-coverage demand-raster coverage-raster)
        satisfied-demand (min scaled-capacity reachable-demand)
        used-capacity    (float (/ satisfied-demand capacity-multiplier))]
    (debug "Applying provider" (:id provider) "with capacity" capacity
           "- satisfies" satisfied-demand "over a total of" reachable-demand "demand units")
    (when-not (zero? reachable-demand)
      (let [factor (- 1 (/ satisfied-demand reachable-demand))]
        (demand/multiply-population-under-coverage! demand-raster coverage-raster (float factor))))
    {:id               (:id provider)
     :satisfied-demand satisfied-demand
     :capacity         capacity
     :used-capacity    used-capacity
     :free-capacity    (- capacity used-capacity)}))

(defn raster-do-providers!
  "Process each provider in the collection *in order* by the function f
  (presumably with side effects) and return a vector with the results; intended
  to be used with raster-measure-provider or raster-apply-provider!

  For example,
  (raster-do-providers! (filter :applicable? providers)
                        (partial raster-apply-provider! demand-raster capacity-multiplier))
  "
  [providers f]
  (reduce
   (fn [results provider]
     (conj results (f provider)))
   []
   providers))

(defn compute-initial-scenario-by-raster
  [engine project]
  (let [project-id           (:id project)
        providers            (providers-in-project (:providers-set engine) project)
        applicable-providers (filter :applicable? providers)
        demand-raster        (project-base-demand-raster project)
        base-demand          (demand/count-population demand-raster)
        capacity-multiplier  (get-in (:config project) [:providers :capacity])
        scenario-filename    (str "initial-" (java.util.UUID/randomUUID))]
    (debug "Base scenario demand:" base-demand)
    (debug "Applying" (count applicable-providers) "providers")

    (let [applied-providers            (raster-do-providers! applicable-providers
                                                             (partial raster-apply-provider! demand-raster capacity-multiplier))
          unsatisfied-demand           (demand/count-population demand-raster)
          quartiles                    (demand/compute-population-quartiles demand-raster)
          providers-unsatisfied-demand (raster-do-providers! providers
                                                             (partial raster-measure-provider demand-raster capacity-multiplier))
          raster-data-path             (scenario-raster-data-path project-id scenario-filename)
          raster-map-path              (scenario-raster-map-path project-id scenario-filename)]
      (io/make-parents raster-data-path)
      (raster/write-raster demand-raster raster-data-path)
      (raster/write-raster (demand/build-renderable-population demand-raster quartiles) raster-map-path)

      (debug "Wrote" raster-data-path)
      (debug "Unsatisfied demand:" unsatisfied-demand)

      {:raster-path      (scenario-raster-path project-id scenario-filename)
       :source-demand    base-demand
       :pending-demand   unsatisfied-demand
       :covered-demand   (- base-demand unsatisfied-demand)
       :demand-quartiles quartiles
       :providers-data   (merge-providers applied-providers providers-unsatisfied-demand)})))

(defn compute-scenario-by-raster
  [engine project initial-scenario scenario]
  (let [project-id             (:id project)
        scenario-id            (:id scenario)
        initial-providers      (providers-in-project (:providers-set engine) project)
        resolved-changeset     (resolve-coverages (:coverage engine)
                                                  project
                                                  (:changeset scenario)
                                                  {:initial-providers   initial-providers
                                                   :scenario-geom-cache (:new-providers-geom scenario)})
        changed-providers      (:changeset resolved-changeset)
        new-providers-geom     (:geom-cache resolved-changeset)
        capacity-multiplier    (get-in (:config project) [:providers :capacity])
        base-demand            (get-in project [:engine-config :source-demand])
        quartiles              (get-in project [:engine-config :demand-quartiles])
        demand-raster-name     (:raster initial-scenario)
        demand-raster          (raster/read-raster (scenario-raster-full-path demand-raster-name))
        initial-providers-data (:providers-data initial-scenario)
        scenario-filename      (str (format "%03d-" scenario-id) (java.util.UUID/randomUUID))]

    (debug "Base scenario demand:" base-demand)
    (debug "Applying" (count changed-providers) "changes")

    (let [applied-changes              (raster-do-providers! changed-providers
                                                             (partial raster-apply-provider! demand-raster capacity-multiplier))
          unsatisfied-demand           (demand/count-population demand-raster)
          providers-unsatisfied-demand (raster-do-providers! (merge-providers initial-providers changed-providers)
                                                             (partial raster-measure-provider demand-raster capacity-multiplier))
          raster-data-path             (scenario-raster-data-path project-id scenario-filename)
          raster-map-path              (scenario-raster-map-path project-id scenario-filename)]
      (raster/write-raster demand-raster raster-data-path)
      (raster/write-raster (demand/build-renderable-population demand-raster quartiles) raster-map-path)

      (debug "Wrote" raster-data-path)
      (debug "Scenario unsatisfied demand:" unsatisfied-demand)

      {:raster-path        (scenario-raster-path project-id scenario-filename)
       :pending-demand     unsatisfied-demand
       :covered-demand     (- base-demand unsatisfied-demand)
       :new-providers-geom new-providers-geom
       :providers-data     (merge-providers initial-providers-data applied-changes providers-unsatisfied-demand)})))

;; POINT SCENARIOS
;; -------------------------------------------------------------------------------------------------

(defn sum-map
  [coll f]
  (reduce + (map f coll)))

(defn update-source
  [source provider total-demand project-capacity]
  (let [ratio          (float (/ (:quantity source) total-demand))
        unsatisfied    (double (max 0 (- (:quantity source) (* (* (:capacity provider) project-capacity) ratio))))
        updated-source (assoc source :quantity unsatisfied)]
    updated-source))

(defn need-to-update-source?
  [source ids]
  (and (ids (:id source))
       (> (:quantity source) 0)))

(defn update-source-if-needed
  [source ids provider total-demand project-capacity]
  (if (need-to-update-source? source ids)
    (update-source source provider total-demand project-capacity)
    source))

#_(defn compute-initial-scenario-by-point
    [engine project]
    (let [provider-set-id  (:provider-set-id project)
          providers        (project-providers engine project) ;sort by capacity
          sources          (sources-set/list-sources-in-set (:sources-set engine) (:source-set-id project))
          algorithm        (:coverage-algorithm project)
          filter-options   (get-in project [:config :coverage :filter-options])
          project-capacity (get-in project [:config :providers :capacity])
          fn-sources-under (fn [provider] (sources-set/list-sources-under-provider-coverage (:sources-set engine) (:source-set-id project) (:id provider) algorithm filter-options))
          fn-select-by-id  (fn [sources ids] (filter (fn [source] (ids (:id source))) sources))
          result-step1     (reduce ; over providers
                            (fn [computed-state {:keys [capacity] :as provider}]
                              (let [providers                 (:providers computed-state)
                                    sources                   (:sources computed-state)
                                    id-sources-under-coverage (set (map :id (fn-sources-under provider)))         ; create set with sources' id
                                    sources-under-coverage    (fn-select-by-id sources id-sources-under-coverage) ; updated sources under coverage
                                    total-demand              (sum-map sources-under-coverage :quantity)        ; total demand requested to current provider
                                    updated-sources           (map (fn [source]
                                                                     (update-source-if-needed source id-sources-under-coverage provider total-demand project-capacity))
                                                                   sources)
                                    satisfied-demand          (min (* capacity project-capacity) total-demand)
                                    updated-provider          (assoc provider :satisfied-demand satisfied-demand
                                                                     :free-capacity (- capacity (float (/ satisfied-demand project-capacity))))]
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
                                    (assoc provider :unsatisfied-demand total-demand
                                           :required-capacity (float (/ total-demand project-capacity)))))
                                (:providers result-step1))]
      (let [initial-quantities        (reduce (fn [dic {:keys [id quantity] :as source}] (assoc dic id quantity)) {} sources)
            updated-sources           (map (fn [s] (assoc (select-keys s [:id :quantity :lat :lon]) :initial-quantity (get initial-quantities (:id s)))) (:sources result-step1))
            updated-providers         result-step2
            total-sources-demand      (sum-map sources :quantity)
            total-satisfied-demand    (sum-map updated-providers :satisfied-demand)
            total-unsatisfied-demand  (sum-map updated-providers :unsatisfied-demand)]

        {:raster-path       nil
         :source-demand     total-sources-demand
         :pending-demand    total-unsatisfied-demand
         :covered-demand    total-satisfied-demand
         :demand-quartiles  nil
         :providers-data    updated-providers
         :sources-data      updated-sources})))

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
  [{:keys [id coverage-geom] :as change} coverage-fn new-providers-geom]
  (let [coverage-geom (get new-providers-geom id)
        change (assoc (select-keys change [:capacity :location]) :id id)]
    (if coverage-geom
      (merge change coverage-geom)
      (assoc change :coverage-geom (coverage-fn change)))))

(defn compute-scenario-by-point
  [engine project {:keys [changeset providers-data sources-data new-providers-geom] :as scenario}]
  (let [algorithm        (:coverage-algorithm project)
        filter-options   (get-in project [:config :coverage :filter-options])
        criteria         (merge {:algorithm (keyword algorithm)} filter-options)
        as-geojson       (fn [geom] (:geom (coverage/geometry-intersected-with-project-region (:coverage engine) geom (:region-id project))))
        coverage-fn      (fn [{:keys [location id]}]
                           (try
                             (coverage/compute-coverage (:coverage engine) location criteria)
                             (catch Exception e
                               (throw (ex-info "New provider failed at computation" (assoc (ex-data e) :id id))))))
        providers        (map #(change-to-provider % (comp as-geojson coverage-fn) new-providers-geom) changeset)
        sources          sources-data
        fn-sources-under (fn [provider] (sources-under engine (:source-set-id project) provider algorithm filter-options))
        fn-filter-by-id  (fn [sources ids] (filter (fn [source] (ids (:id source))) sources))
        project-capacity (get-in project [:config :providers :capacity])
        result-step1     (reduce ; over providers
                          (fn [computed-state {:keys [capacity] :as provider}]
                            (let [providers                 (:providers computed-state)
                                  sources                   (:sources computed-state)
                                  id-sources-under-coverage (set (map :id (fn-sources-under provider)))         ; create set with sources' id
                                  sources-under-coverage    (fn-filter-by-id sources id-sources-under-coverage) ; take only the sources under coverage (using the id to filter)
                                  total-demand              (sum-map sources-under-coverage :quantity)          ; total demand requested to current provider
                                  updated-sources           (map (fn [source] (update-source-if-needed source id-sources-under-coverage provider total-demand project-capacity)) sources)
                                  satisfied-demand          (min (* capacity project-capacity) total-demand)]
                              {:providers (conj providers (assoc provider :satisfied-demand satisfied-demand
                                                                 :free-capacity (- capacity (float (/ satisfied-demand project-capacity)))))
                               :sources updated-sources}))
                          {:providers nil
                           :sources sources}
                          providers)
        result-step2     (map (fn [provider]  ; resolve unsatisfied demand per provider (for all providers!)
                                (let [sources                   (:sources result-step1)
                                      id-sources-under-coverage (set (map :id (fn-sources-under provider)))
                                      sources-under-coverage    (fn-filter-by-id sources id-sources-under-coverage) ; updated sources under coverage
                                      total-demand              (sum-map sources-under-coverage :quantity)]
                                  (assoc provider :unsatisfied-demand total-demand
                                         :required-capacity (float (/ total-demand project-capacity)))))
                              (concat providers-data (:providers result-step1)))]
    (let [updated-sources          (:sources result-step1)
          updated-providers        (map #(dissoc % :coverage-geom) result-step2)
          changes-geom             (reduce (fn [dic {:keys [id] :as provider}]
                                             (when-not (get dic id) (assoc dic id (select-keys provider [:coverage-geom]))))
                                           new-providers-geom providers)
          total-sources-demand     (sum-map sources :quantity)
          total-satisfied-demand   (sum-map updated-providers :satisfied-demand)
          total-unsatisfied-demand (sum-map updated-providers :unsatisfied-demand)]
      {:raster-path      nil
       :pending-demand   total-unsatisfied-demand
       :covered-demand   total-satisfied-demand
       :providers-data   updated-providers
       :sources-data     updated-sources
       :new-providers-geom (merge new-providers-geom changes-geom)})))


;; COMPONENT ENTRY POINTS
;; -------------------------------------------------------------------------------------------------

(defn compute-initial-scenario
  [engine project]
  (debug "Computing initial scenario for project" (:id project))
  (let [source-set (sources-set/get-source-set-by-id (:sources-set engine) (:source-set-id project))]
    (if (= (:type source-set) "points")
      nil #_(compute-initial-scenario-by-point engine project)
      (compute-initial-scenario-by-raster engine project))))

(defn compute-scenario
  [engine project initial-scenario scenario]
  (let [source-set (sources-set/get-source-set-by-id (:sources-set engine) (:source-set-id project))]
    (if (= (:type source-set) "points")
      (compute-scenario-by-point engine project initial-scenario scenario)
      (compute-scenario-by-raster engine project initial-scenario scenario))))

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
  (compute-scenario [engine project initial-scenario scenario]
    (compute-scenario engine project initial-scenario scenario))
  (search-optimal-location [engine project source]
    (suggestions/search-optimal-location engine project source)))

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

