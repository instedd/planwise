(ns planwise.component.engine
  (:require [planwise.boundary.engine :as boundary]
            [planwise.boundary.projects2 :as projects2]
            [planwise.boundary.providers-set :as providers-set]
            [planwise.boundary.sources :as sources-set]
            [planwise.boundary.coverage :as coverage]
            [planwise.boundary.regions :as regions]
            [planwise.boundary.runner :as runner]
            [planwise.engine.raster :as raster]
            [planwise.engine.common :refer [provider-coverage-raster-path providers-in-project]]
            [planwise.component.coverage.rasterize :as rasterize]
            [planwise.engine.suggestions :as suggestions]
            [planwise.engine.demand :as demand]
            [planwise.util.geo :as geo]
            [planwise.util.files :as files]
            [planwise.util.numbers :refer [abs float=]]
            [planwise.model.providers :refer [merge-providers merge-provider]]
            [planwise.util.collections :refer [sum-by merge-collections-by]]
            [integrant.core :as ig]
            [clojure.java.io :as io]
            [taoensso.timbre :as timbre]
            [clojure.set :as set]
            [clojure.string :as str])
  (:import [clojure.lang ExceptionInfo]))

(timbre/refer-timbre)

(def max-pixels-threshold
  "Maximum number of pixels for raster scenarios. Sources will be scaled down by
  integer factors for the resulting number of pixels to be below this threshold"
  (* 25 1024 1024))
(def ^:dynamic *script-timeout-ms* 30000)
(def ^:dynamic *bin-timeout-ms* 10000)

;; PATH HELPERS
;; -------------------------------------------------------------------------------------------------

(defn source-set-raster-path
  "Full path to the original source set raster file."
  [{:keys [raster-file]}]
  (str "data/" raster-file))

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
  "Given a raster path from scenario-raster-path, return the full path to the raster file."
  [raster-path]
  (str "data/" raster-path ".tif"))

(defn project-file-path
  "Prefixes a filename with the full path to the project data directory"
  [project-id file-path]
  (str "data/scenarios/" project-id "/" file-path))

(defn project-directory
  [project-id]
  (project-file-path project-id ""))

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

(defn- coverage-criteria-for-project
  "Criteria options from project for usage in coverage/compute-coverage."
  [project]
  (let [coverage-algorithm (keyword (:coverage-algorithm project))
        project-config     (:config project)
        coverage-options   (get-in project-config [:coverage :filter-options])]
    (assoc coverage-options :algorithm coverage-algorithm)))

(defn coverage-for-new-provider
  "If the coverage raster file for the given new provider id doesn't exist or
  the polygon is not present in the cache, compute the coverage raster and
  return the coverage polygon clipped to the region of the project."
  ([coverage project change]
   (coverage-for-new-provider coverage project change {}))
  ([coverage project {:keys [id location] :as change} geom-cache]
   (let [project-id  (:id project)
         raster-path (when (= (:type-of-source-demand project) "raster")
                       (new-provider-coverage-raster-path project-id id))
         cached-geom (get geom-cache id)]
     (if (and (some? cached-geom) (if-some [file (io/as-file raster-path)] (.exists file) true))
       ;; geometry already computed and raster file exists
       {:geom        cached-geom
        :raster-path raster-path}
       ;; either geometry or raster file don't exist
       (try
         (when raster-path (io/delete-file raster-path true))
         (let [criteria     (merge (coverage-criteria-for-project project) (when raster-path {:raster raster-path}))
               geom         (coverage/compute-coverage coverage location criteria)
               clipped-geom (:geom (coverage/geometry-intersected-with-project-region coverage geom (:region-id project)))]
           {:geom        clipped-geom
            :raster-path raster-path})
         (catch Exception e
           (throw (ex-info "New provider failed at computation"
                           (assoc (ex-data e) :id id)
                           e))))))))

;; TODO: change this function to only return the changeset w/coverages resolved,
;; including the geometries for new providers; then later in the computation
;; algorithm, project the geometries to build the updated cache
(defn resolve-coverages
  [coverage project changeset {:keys [initial-providers geom-cache]}]
  (let [providers-index (group-by :id initial-providers)]
    (reduce (fn [changeset {:keys [id] :as change}]
              (case (:action change)
                "create-provider"
                (let [result  (coverage-for-new-provider coverage project change geom-cache)
                      change' (assoc change
                                     :coverage-geojson (:geom result)
                                     :coverage-raster-path (:raster-path result))]
                  (conj changeset change'))

                ("upgrade-provider" "increase-provider")
                (let [initial-provider     (first (get providers-index id))
                      coverage-raster-path (:coverage-raster-path initial-provider)
                      coverage-id          (:coverage-id initial-provider)
                      change'              (assoc change
                                                  :coverage-id coverage-id
                                                  :coverage-raster-path coverage-raster-path)]
                  (conj changeset change'))

                changeset))

            []
            changeset)))

(defn build-geom-cache
  "Build the GeoJSON coverage cache to save in the scenario, containing coverages for new providers."
  [providers]
  (into {}
        (map (fn [{:keys [id coverage-geojson]}]
               (when coverage-geojson
                 [id coverage-geojson]))
             providers)))

;; RASTER SCENARIOS
;; -------------------------------------------------------------------------------------------------

(defn read-raster-from-source-set
  "Given a source set, return the raster basic information (path, pixel size,
  transformation matrix) to the original Tiff."
  [source-set]
  (debug (str "Reading raster " (:raster-file source-set) " from source set " (:id source-set)))
  (let [raster-path (source-set-raster-path source-set)]
    (try
      (let [source-raster (raster/read-raster-without-data raster-path)]
        (debug (str "Raster file is " (:xsize source-raster) "x" (:ysize source-raster)))
        source-raster)
      (catch ExceptionInfo e
        (warn "Failed to load source set raster file" {:source-set source-set})))))

(defn region-inside-raster?
  "Check if region is fully contained inside the bounding box of a source raster."
  [regions region-id raster]
  (let [buffer-pixels 10
        envelope      (raster/raster-envelope raster buffer-pixels)
        region-ids    (set (regions/enum-regions-inside-envelope regions envelope))]
    (contains? region-ids region-id)))

(defn estimate-envelope-raster-size
  "Compute the approximate size (in pixels) for an area given an envelope and a
  raster to get the resolution."
  [raster envelope]
  (let [{:keys [xres yres]}                       (raster/raster-resolution raster)
        {:keys [min-lon max-lon min-lat max-lat]} envelope]
    {:xsize (Math/ceil (/ (- max-lon min-lon) (abs xres)))
     :ysize (Math/ceil (/ (- max-lat min-lat) (abs yres)))}))

(defn compute-down-scaling-factor
  "Compute an integer down-scaling factor to reduce a raster size to a maximum
  number of pixels. Scale should be applied uniformily to width and height."
  [{:keys [xsize ysize]}]
  (Math/ceil (Math/sqrt (/ (* xsize ysize) max-pixels-threshold))))

(defn resize-raster
  "Resize a raster by a scale factor using external tool gdalwarp. Returns the
  original raster if scale factor is 1."
  [engine raster scale-factor work-dir]
  (if (float= 1.0 scale-factor)
    raster
    (let [input-path          (:file-path raster)
          output-path         (str work-dir "/source-scaled.tif")
          {:keys [xres yres]} (raster/raster-resolution raster)
          args                (map str ["-i" input-path
                                        "-o" output-path
                                        "-r" (* scale-factor xres) (* scale-factor yres)])]
      (io/make-parents output-path)
      (io/delete-file output-path :silent)
      (runner/run-external (:runner engine) :scripts *script-timeout-ms* "resize-raster" args)
      (raster/read-raster-without-data output-path))))

(defn crop-raster-by-cutline
  "Crop a raster by a given GeoJSON contour using external tool gdalwarp."
  [engine raster geojson work-dir]
  (let [cutline-path        (str work-dir "/cutline.geojson")
        input-path          (:file-path raster)
        output-path         (str work-dir "/source-cropped.tif")
        {:keys [xres yres]} (raster/raster-resolution raster)
        args                (map str ["-i" input-path
                                      "-o" output-path
                                      "-c" cutline-path
                                      "-r" xres yres])]
    (io/make-parents cutline-path)
    (spit cutline-path geojson)
    (io/delete-file output-path :silent)
    (runner/run-external (:runner engine) :scripts *script-timeout-ms* "crop-source-raster" args)
    (raster/read-raster-without-data output-path)))

(defn count-raster-demand
  "Using the external binary aggregate-population, compute the sum of all values
  of the given raster."
  [engine raster]
  (let [input-path (:file-path raster)
        args       [input-path]
        output     (runner/run-external (:runner engine) :bin *bin-timeout-ms* "aggregate-population" args)]
    (-> output
        (str/split #"\s+")
        first
        Long/parseLong)))

(defn compute-resize-factor
  "Given two rasters (presumably the second being a down-scaled version of the
  first), compute the scaling factor to apply to each pixel such that the
  aggregate of the values of the pixels are equal.
  Since we are using PPP (population per pixel) rasters, we need this to account
  for the down-scaling done for optimization."
  [engine original-raster resized-raster]
  (if (= original-raster resized-raster)
    1.0
    (let [original-demand (count-raster-demand engine original-raster)
          resized-demand (count-raster-demand engine resized-raster)]
      (double (/ original-demand resized-demand)))))

(defn process-base-demand-raster
  "Reads the source original raster file, resizes to a manageable resolution,
  crops to the project region and applies scaling and project target factor.
  Returns the modified resulting raster in-memory for further use in computing
  the initial scenario.
  May output temporary files in the project scenarios data directory."
  [engine project]
  (let [project-id      (:id project)
        project-path    (project-directory project-id)
        source-set      (:source-set project)
        source-raster   (read-raster-from-source-set source-set)
        regions-service (:regions engine)
        region-id       (:region-id project)]

    ;; Check that the source raster is readable
    (when (nil? source-raster)
      (throw (ex-info "Source raster is not readable" {:project-id project-id
                                                       :source-set source-set})))

    ;; Check that the raster contains the project region
    (when-not (region-inside-raster? regions-service region-id source-raster)
      (throw (ex-info "Region is not contained in source raster. Cannot compute scenario."
                      {:project-id project-id
                       :region-id  region-id
                       :source-set source-set})))

    ;; Estimate project raster size without scaling down source raster
    (let [region          (regions/get-region-geometry regions-service region-id)
          region-envelope (geo/bbox->envelope (:bbox region))
          estimated-size  (estimate-envelope-raster-size source-raster region-envelope)
          scale-factor    (compute-down-scaling-factor estimated-size)]
      (debug (str "Region envelope: " (pr-str region-envelope)))
      (debug (str "Estimated raster size (before scaling): " (pr-str estimated-size)))
      (debug (str "Down scale factor to apply: " scale-factor))

      ;; Scale down the source raster if necessary
      (let [resized-raster       (resize-raster engine source-raster scale-factor project-path)
            resize-demand-factor (compute-resize-factor engine source-raster resized-raster)]
        (debug (str "Resized raster is " (:xsize resized-raster) "x" (:ysize resized-raster)))
        (debug (str "Need to apply a resize factor of " resize-demand-factor))

        ;; Cut the source raster using the region outline
        (let [cropped-raster (crop-raster-by-cutline engine resized-raster (:geojson region) project-path)]
          (debug "Cropped source raster to working region:" (:file-path cropped-raster))
          (debug (str "Project raster is " (:xsize cropped-raster) "x" (:ysize cropped-raster)))

          ;; Read the final cropped raster and apply the resize factor and the
          ;; project's target factor
          (let [project-config (:config project)
                target-factor  (/ (get-in project-config [:demographics :target]) 100)
                project-raster (raster/read-raster (:file-path cropped-raster))]

            (-> project-raster
                (doto (demand/multiply-population! (float (* resize-demand-factor target-factor))))
                (assoc :scaling-factor resize-demand-factor))))))))

(defn raster-measure-provider
  "Measures the unsatisfied demand and computes the required (extra) capacity
  for a provider in the demand raster."
  [demand-raster capacity-multiplier provider]
  (let [coverage-raster  (raster/read-raster (:coverage-raster-path provider))
        reachable-demand (demand/count-population-under-coverage demand-raster coverage-raster)]
    {:id                 (:id provider)
     :unsatisfied-demand reachable-demand
     :required-capacity  (float (/ reachable-demand capacity-multiplier))}))

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
        demand-raster        (process-base-demand-raster engine project)
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

      {:raster-path       (scenario-raster-path project-id scenario-filename)
       :raster-resolution (raster/raster-resolution demand-raster)
       :scaling-factor    (:scaling-factor demand-raster)
       :source-demand     base-demand
       :pending-demand    unsatisfied-demand
       :covered-demand    (- base-demand unsatisfied-demand)
       :demand-quartiles  quartiles
       :providers-data    (merge-providers applied-providers providers-unsatisfied-demand)})))

(defn compute-scenario-by-raster
  [engine project initial-scenario scenario]
  (let [project-id             (:id project)
        scenario-id            (:id scenario)
        initial-providers      (providers-in-project (:providers-set engine) project)
        changed-providers      (resolve-coverages (:coverage engine)
                                                  (assoc project :type-of-source-demand "raster")
                                                  (:changeset scenario)
                                                  {:initial-providers   initial-providers
                                                   :scenario-geom-cache (:new-providers-geom scenario)})
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
      (io/make-parents raster-data-path)
      (raster/write-raster demand-raster raster-data-path)
      (raster/write-raster (demand/build-renderable-population demand-raster quartiles) raster-map-path)

      (debug "Wrote" raster-data-path)
      (debug "Scenario unsatisfied demand:" unsatisfied-demand)

      {:raster-path        (scenario-raster-path project-id scenario-filename)
       :pending-demand     unsatisfied-demand
       :covered-demand     (- base-demand unsatisfied-demand)
       :new-providers-geom (build-geom-cache changed-providers)
       :providers-data     (merge-providers initial-providers-data applied-changes providers-unsatisfied-demand)})))

;; POINT SCENARIOS
;; -------------------------------------------------------------------------------------------------

(defn project-base-sources
  [sources-component project]
  (let [region-id     (:region-id project)
        source-set-id (:source-set-id project)
        target-factor (/ (get-in project [:config :demographics :target]) 100)
        sources       (sources-set/get-sources-from-set-in-region sources-component source-set-id region-id)
        sources'      (map (fn [source]
                             (let [quantity        (:quantity source)
                                   scaled-quantity (float (* target-factor quantity))]
                               (assoc source
                                      :quantity scaled-quantity
                                      :initial-quantity scaled-quantity)))
                           sources)]
    (into {} (map (juxt :id identity) sources'))))

(defn resolve-covered-sources
  "For each provider, find the sources covered by it (how depends on whether the provider is new or
  from the provider set) and assoc them to it for later."
  [sources-component source-set-id providers sources]
  (debug "sources" (keys sources))
  (let [source-ids     (set (keys sources))
        covered-ids-fn (fn [{:keys [coverage-id coverage-geojson] :as provider}]
                         (if (some? coverage-geojson)
                           (sources-set/enum-sources-under-geojson-coverage sources-component
                                                                            source-set-id
                                                                            coverage-geojson)
                           (sources-set/enum-sources-under-provider-coverage sources-component
                                                                             source-set-id
                                                                             coverage-id)))]
    (map (fn [provider]
           (let [covered-ids             (set (covered-ids-fn provider))
                 covered-ids-in-scenario (set/intersection source-ids covered-ids)]
             (assoc provider :covered-source-ids covered-ids-in-scenario)))
         providers)))

(defn point-measure-provider
  "Measures the unsatisfied demand of the sources covered by the provider."
  [capacity-multiplier provider sources]
  (let [reachable-sources (map sources (:covered-source-ids provider))
        reachable-demand  (sum-by :quantity reachable-sources)]
    [{:id                 (:id provider)
      :unsatisfied-demand reachable-demand
      :required-capacity  (float (/ reachable-demand capacity-multiplier))}
     sources]))

(defn point-apply-provider!
  "Distributes capacity of provider over all covered sources proportionally to their demand over the
  total provider reachable demand."
  [capacity-multiplier provider sources]
  (let [reachable-sources (map sources (:covered-source-ids provider))
        capacity          (:capacity provider)
        scaled-capacity   (* capacity capacity-multiplier)
        reachable-demand  (sum-by :quantity reachable-sources)
        satisfied-demand  (min scaled-capacity reachable-demand)
        used-capacity     (float (/ satisfied-demand capacity-multiplier))]
    (debug "Applying provider" (:id provider) "with capacity" capacity
           "- satisfies" satisfied-demand "over a total of" reachable-demand "demand units")
    (let [sources' (if (zero? reachable-demand)
                     sources
                     (let [factor (float (- 1 (/ satisfied-demand reachable-demand)))]
                       (reduce (fn [sources id]
                                 (update-in sources [id :quantity] * factor))
                               sources
                               (:covered-source-ids provider))))]
      [{:id               (:id provider)
        :satisfied-demand satisfied-demand
        :capacity         capacity
        :used-capacity    used-capacity
        :free-capacity    (- capacity used-capacity)}
       sources'])))

(defn point-do-providers!
  [providers f sources]
  (reduce (fn [[result sources'] provider]
            (println "sources'" sources')
            (let [[provider' sources''] (f provider sources')]
              (println "sources''" sources'')
              [(conj result provider') sources'']))
          [[] sources]
          providers))

(defn compute-initial-scenario-by-point
  [engine project]
  (let [project-id           (:id project)
        source-set-id        (:source-set-id project)
        sources-component    (:sources-set engine)
        initial-sources      (project-base-sources sources-component project)
        initial-providers    (providers-in-project (:providers-set engine) project)
        providers            (resolve-covered-sources sources-component
                                                      source-set-id
                                                      initial-providers
                                                      initial-sources)
        applicable-providers (filter :applicable? providers)
        base-demand          (sum-by :initial-quantity (vals initial-sources))
        capacity-multiplier  (get-in (:config project) [:providers :capacity])]
    (debug "Base scenario demand:" base-demand)
    (debug "Applying" (count applicable-providers) "providers")

    (let [[applied-providers
           sources]          (point-do-providers! applicable-providers
                                                  (partial point-apply-provider! capacity-multiplier)
                                                  initial-sources)
          unsatisfied-demand (sum-by :quantity (vals sources))
          [providers-unsatisfied-demand
           sources]          (point-do-providers! providers
                                                  (partial point-measure-provider capacity-multiplier)
                                                  sources)]
      (debug "Unsatisfied demand:" unsatisfied-demand)

      {:sources-data   (vals sources)
       :source-demand  base-demand
       :pending-demand unsatisfied-demand
       :covered-demand (- base-demand unsatisfied-demand)
       :providers-data (merge-providers applied-providers providers-unsatisfied-demand)})))

(defn compute-scenario-by-point
  [engine project initial-scenario scenario]
  (let [sources                      (into {} (map (juxt :id identity) (:sources-data initial-scenario)))
        initial-providers            (providers-in-project (:providers-set engine) project)
        changeset-with-coverage      (resolve-coverages (:coverage engine)
                                                        project
                                                        (:changeset scenario)
                                                        {:initial-providers   initial-providers
                                                         :scenario-geom-cache (:new-providers-geom scenario)})
        changed-providers            (resolve-covered-sources (:sources-set engine)
                                                              (:source-set-id project)
                                                              changeset-with-coverage
                                                              sources)
        initial-with-covered-sources (resolve-covered-sources (:sources-set engine)
                                                              (:source-set-id project)
                                                              initial-providers
                                                              sources)
        capacity-multiplier          (get-in (:config project) [:providers :capacity])
        base-demand                  (get-in project [:engine-config :source-demand])
        initial-providers-data       (:providers-data initial-scenario)]

    (debug "Base scenario demand:" base-demand)
    (debug "Applying" (count changed-providers) "changes")

    (let [[applied-changes
           sources']         (point-do-providers! changed-providers
                                                  (partial point-apply-provider! capacity-multiplier)
                                                  sources)
          unsatisfied-demand (sum-by :quantity (vals sources'))
          [providers-unsatisfied-demand
           sources']         (point-do-providers! (merge-providers initial-with-covered-sources changed-providers)
                                                  (partial point-measure-provider capacity-multiplier)
                                                  sources')]
      (debug "Unsatisfied demand:" unsatisfied-demand)

      {:sources-data       (vals sources')
       :pending-demand     unsatisfied-demand
       :covered-demand     (- base-demand unsatisfied-demand)
       :new-providers-geom (build-geom-cache changeset-with-coverage)
       :providers-data     (merge-providers initial-providers-data applied-changes providers-unsatisfied-demand)})))


;; COMPONENT ENTRY POINTS
;; -------------------------------------------------------------------------------------------------

(defn compute-initial-scenario
  [engine project]
  (debug "Computing initial scenario for project" (:id project))
  (let [source-set (sources-set/get-source-set-by-id (:sources-set engine) (:source-set-id project))
        project    (assoc project :source-set source-set)]
    (case (:type source-set)
      "points" (compute-initial-scenario-by-point engine project)
      "raster" (compute-initial-scenario-by-raster engine project)
      (throw (ex-info "Invalid source set type for scenario computation" {:project-id      (:id project)
                                                                          :source-set-id   (:source-set-id project)
                                                                          :source-set-type (:type source-set)})))))

(defn compute-scenario
  [engine project initial-scenario scenario]
  (debug "Computing scenario" (:id scenario) "for project" (:id project))
  (let [source-set (sources-set/get-source-set-by-id (:sources-set engine) (:source-set-id project))]
    (case (:type source-set)
      "points" (compute-scenario-by-point engine project initial-scenario scenario)
      "raster" (compute-scenario-by-raster engine project initial-scenario scenario)
      (throw (ex-info "Invalid source set type for scenario computation" {:project-id      (:id project)
                                                                          :scenario-id     (:id scenario)
                                                                          :source-set-id   (:source-set-id project)
                                                                          :source-set-type (:type source-set)})))))

(defn clear-project-cache
  [this project-id]
  (let [scenarios-path (str "data/scenarios/" project-id)]
    (files/delete-files-recursively scenarios-path true)))

(defrecord Engine [providers-set sources-set coverage regions runner]
  boundary/Engine
  (compute-initial-scenario [engine project]
    (compute-initial-scenario engine project))
  (clear-project-cache [engine project]
    (clear-project-cache engine project))
  (compute-scenario [engine project initial-scenario scenario]
    (compute-scenario engine project initial-scenario scenario))
  (search-optimal-locations [engine project source]
    (suggestions/search-optimal-location engine project source))
  (search-optimal-interventions [engine project scenario settings]
    (suggestions/get-sorted-providers-interventions engine project scenario settings)))

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

  (compute-initial-scenario (dev/engine) (projects2/get-project (dev/projects2) 2))

  nil)
