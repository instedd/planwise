(ns planwise.engine.suggestions
  (:require [planwise.boundary.providers-set :as providers-set]
            [planwise.boundary.sources :as sources-set]
            [planwise.component.coverage.greedy-search :as gs]
            [planwise.boundary.coverage :as coverage]
            [planwise.engine.raster :as raster]
            [planwise.engine.common :as common]
            [planwise.component.coverage.rasterize :as rasterize]
            [planwise.engine.demand :as demand]
            [planwise.util.files :as files]
            [taoensso.timbre :as timbre]))

(timbre/refer-timbre)

(def suggest-max-pixels
  "Maximum number of pixels for rasters used for running the suggest location
  algorithms"
  (* 1024 1024))

;; Location suggestions algorithm =============================================
;;

(defn count-under-geometry
  [engine polygon {:keys [raster original-sources source-set-id geom-set]}]
  (if raster
    (let [coverage (raster/create-raster (rasterize/rasterize polygon))]
      (demand/count-population-under-coverage raster coverage))
    (let [ids (set (sources-set/enum-sources-under-coverage (:sources-set engine) source-set-id polygon))]
      (reduce (fn [sum {:keys [quantity id]}] (+ sum (if (ids id) quantity 0))) 0 original-sources))))

(defn update-visited
  [{:keys [xsize geotransform data] :as raster} visited]
  (if (empty? (vec visited))
    raster
    (let [idxs (mapv (fn [coord] (let [[x y] (gs/coord->pixel geotransform coord)]
                                   (+ (* y xsize) x))) (remove empty? (vec visited)))]
      (doseq [i idxs]
        (aset data i (float 0)))
      (assert (every? zero? (map #(aget data %) idxs)))
      (raster/create-raster-from-existing raster data))))

(defn get-demand-source-updated
  [engine {:keys [sources-data search-path demand-quartiles]} polygon get-update]
  (if search-path

    (let [{:keys [demand visited]} get-update
          raster (update-visited (raster/read-raster search-path) visited)
          coverage-raster (raster/create-raster (rasterize/rasterize polygon))]

      (demand/multiply-population-under-coverage! raster coverage-raster (float 0))
      (assert (zero? (count-under-geometry engine polygon {:raster raster})))
      (raster/write-raster raster search-path)
      (gs/get-saturated-locations {:raster raster} demand-quartiles))

    (coverage/locations-outside-polygon (:coverage engine) polygon (:demand get-update))))

(defn get-coverage-for-suggestion
  [engine {:keys [criteria region-id project-capacity]} {:keys [sources-data search-path] :as source} {:keys [provider-id coord get-avg get-update]}]
  (let [updated-criteria      (if sources-data criteria (merge criteria {:raster search-path}))
        [lon lat :as coord]   coord
        polygon               (if coord
                                (coverage/compute-coverage-polygon (:coverage engine) {:lat lat :lon lon} updated-criteria)
                                ;; FIXME
                                nil #_(:geom (providers-set/get-coverage
                                              (:providers-set engine)
                                              provider-id
                                              {:algorithm (name (:algorithm criteria))
                                               :region-id region-id
                                               :filter-options (dissoc criteria :algorithm)})))
        population-reacheable (count-under-geometry engine polygon source)
        coverage-info   {:coverage population-reacheable
                         :required-capacity (/ population-reacheable project-capacity)}
        extra-info-for-new-provider {:coverage-geom (:geom (coverage/geometry-intersected-with-project-region (:coverage engine) polygon region-id))
                                     :location {:lat lat :lon lon}}]

    (cond get-avg    {:max (coverage/get-max-distance-from-geometry (:coverage engine) polygon)}
          get-update {:location-info (merge coverage-info extra-info-for-new-provider)
                      :updated-demand (get-demand-source-updated engine source polygon get-update)}
          provider-id coverage-info
          :other     (merge coverage-info extra-info-for-new-provider))))

(defn search-optimal-location
  [engine {:keys [engine-config config provider-set-id coverage-algorithm] :as project} {:keys [raster sources-data] :as source}]
  (let [raster        (when raster (raster/read-raster (str "data/" (:raster source) ".tif")))
        search-path   (when raster (files/create-temp-file (str "data/scenarios/" (:id project) "/coverage-cache/") "new-provider-" ".tif"))
        demand-quartiles (:demand-quartiles engine-config)
        source        (assoc source :raster raster
                             :initial-set (when raster (gs/get-saturated-locations {:raster raster} demand-quartiles))
                             :search-path search-path
                             :demand-quartiles demand-quartiles
                             :source-set-id (:source-set-id project)
                             :original-sources sources-data
                             :sources-data (gs/get-saturated-locations {:sources-data (remove #(-> % :quantity zero?) sources-data)} nil))
        criteria  (assoc (get-in config [:coverage :filter-options]) :algorithm (keyword coverage-algorithm))
        project-info {:criteria criteria
                      :region-id (:region-id project)
                      :project-capacity (get-in config [:providers :capacity])}
        coverage-fn (fn [val props] (try
                                      (get-coverage-for-suggestion engine project-info source (assoc props :coord val))
                                      (catch Exception e
                                        (warn (str "Failed to compute coverage for coordinates " val) e))))]
    (when raster (raster/write-raster-file raster search-path))
    ;; FIXME
    (let [bound    nil #_(when provider-set-id (:avg-max (providers-set/get-radius-from-computed-coverage (:providers-set engine) criteria provider-set-id)))
          locations (gs/greedy-search 10 source coverage-fn demand-quartiles {:bound bound :n 20})]
      locations)))


;; Intervention suggestions algorithm =========================================
;;

;TODO; shared code with client
(defn- get-investment-from-project-config
  [capacity increasing-costs]
  (let [first     (first increasing-costs)
        last      (last increasing-costs)
        intervals (mapv vector increasing-costs (drop 1 increasing-costs))]
    (cond
      (<= capacity (:capacity first)) (:investment first)
      :else
      (let [[[a b] :as interval] (drop-while (fn [[_ b]] (and (not= last b) (< (:capacity b) capacity))) intervals)
            m     (/ (- (:investment b) (:investment a)) (- (:capacity b) (:capacity a)))]
        (+ (* m (- capacity (:capacity a))) (:investment a))))))

(defn- get-increasing-cost
  [{:keys [capacity action]} {:keys [upgrade-budget increasing-costs no-action-costs]}]
  (let [investment (when-not no-action-costs
                     (if (or (zero? capacity) (nil? capacity))
                       0
                       (get-investment-from-project-config capacity increasing-costs)))]
    (cond no-action-costs 1
          (= action "upgrade-provider") (+ investment (or upgrade-budget 0))
          :else investment)))

(defn get-provider-capacity-and-cost
  [provider settings]
  (let [max-capacity      (:max-capacity settings)
        action-capacity   (if max-capacity
                            (min (:required-capacity provider) (:max-capacity settings))
                            (:required-capacity provider))
        action-cost       (get-increasing-cost
                           {:action (if (:applicable? provider)
                                      "increase-provider"
                                      "upgrade-provider")
                            :capacity action-capacity}
                           settings)]
    (cond
      (< (:available-budget settings) action-cost) nil
      :else {:action-capacity action-capacity
             :action-cost     action-cost})))

(defn insert-in-sorted-coll
  [coll value criteria]
  (sort-by criteria > (conj coll value)))

(defn get-information-from-demand
  [all-providers id]
  (select-keys
   (first (filter #(= id (:id %)) all-providers))
   [:required-capacity]))

(defn get-sorted-providers-interventions
  [engine project {:keys [providers-data changeset] :as scenario} settings]
  (let [{:keys [engine-config config provider-set-id region-id coverage-algorithm]} project
        providers-collection (common/providers-in-project (:providers-set engine) project)]
    (reduce
     (fn [suggestions provider]
       (insert-in-sorted-coll
        suggestions
        (when-let [intervention (get-provider-capacity-and-cost
                                 (merge provider
                                        (get-information-from-demand providers-data (:id provider)))
                                 settings)]
          (merge
           provider
           intervention
           (let [{:keys [action-capacity action-cost]} intervention]
             {:ratio (if (not (zero? action-cost))
                       (/ action-capacity action-cost)
                       0)})))
        :ratio))
     []
     providers-collection)))
