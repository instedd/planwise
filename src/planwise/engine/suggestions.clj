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
            [clojure.set :refer [rename-keys]]
            [taoensso.timbre :as timbre])
  (:import [planwise.engine Algorithm]))

(timbre/refer-timbre)

(defmulti search-optimal-locations (fn [engine project scenario] (:source-type project)))

;; Location suggestions algorithm =============================================
;;

;; Algorithm description
;;
;; Given:
;; - D the set of unsatisfied demand points
;; - N a natural number of suggestions to find
;;
;; 1. Find the source point (or raster pixel) Pmax from the set D with the most
;;    unsatisfied demand, and make P = Pmax
;; 2. Compute coverage C starting from P

;;          vvvv optional vvvv
;; 3. Compute the intersection between the coverage C and the sources in D
;;    We will relax this intersection to just intersect the coverage to _the
;;    extent_ of the points reached by C.
;;    For computing the extent in the raster case, consider only the most
;;    relevant source points (eg. those in the last quartile)
;;    For computing the extent in the point case, if the coverage reaches only
;;    one source, extend the search area by including the nearest source point
;;    to the coverage
;; 4. Find the closest point Pclosest *in the coverage* to the centroid of the
;;    intersection
;; 5. Repeat from 2. until a local maxima is found for the covered demand
;;          ^^^^ optional ^^^^

;; 6. Add P to the list of suggestions, preserving order by the demand covered
;; 7. Remove the source points from D covered by C
;; 8. Repeat from 1. until no more locations can be found, or 2*N locations
;;    have been found (the factor 2 is a guess here; the idea is to keep on
;;    searching to find better solutions down the line)

;; Algorithm iteration core
;; This functions implement the outer loop of the algorithm and are used for
;; both raster and points scenarios
(defn- insert-keep-order
  "Inserts a new location suggestion into the collection, preserving order of
  coverage provided"
  [locations new-location]
  (sort-by :coverage > (conj locations new-location)))

(defn- insert-location-if-better
  "If the location is an improvement over what is already known, insert in order
  and keep collection limited to limit items. Otherwise, append at the end and
  allow the collection to grow."
  [locations new-location limit]
  (let [top-locations    (take limit locations)
        is-top-location? (some (fn [known] (> (:coverage new-location)
                                              (:coverage known)))
                               top-locations)]
    (if is-top-location?
     (take limit (insert-keep-order locations new-location))
     (conj (vec locations) new-location))))

(defn- compute-suggestions
  "Compute up-to limit suggestions for locations and return them in a vector
  ordered by coverage provided"
  [engine find-location-fn run-data limit]
  (let [limit-grow     (* 3 limit)
        max-iterations (* 10 limit)]
    (loop [suggestions []
           run-data    run-data
           iteration   1]
      (let [{new-location :location new-run-data :run-data}
            (find-location-fn engine (assoc run-data :iteration iteration))]
        (if new-location
          (let [suggestions' (insert-location-if-better suggestions new-location limit)]
            ;; if list of locations has grown above limit-grow, it means the
            ;; last few tries have not improved the top suggestions, and we
            ;; don't expect to find improvements down the line; otherwise keep
            ;; exploring until no more locations can be found or the number of
            ;; iterations is exhausted
            (if (and (< (count suggestions') limit-grow)
                     (< iteration max-iterations))
              (recur suggestions' new-run-data (inc iteration))
              (do
                (info (str "Cutting search short; iterated " iteration " times"))
                {:suggestions suggestions'
                 :run-data    new-run-data})))
          ;; no more locations can be found
          (do
            (info (str "No more locations found; iterated " iteration " times"))
            {:suggestions suggestions
             :run-data    new-run-data}))))))

(defn- setup-context-for-suggestions!
  "Setups the coverage context for the search algorithm"
  ([engine project scenario]
   (setup-context-for-suggestions! engine project scenario nil))
  ([engine project scenario raster-resolution]
   ;; FIXME: this will work for now, but it's very re-entrant britle
   (let [context-id [:suggestions (:id project) (:id scenario)]
         options    {:region-id         (:region-id project)
                     :coverage-criteria (common/coverage-criteria-for-project project)
                     :updated-at        (str (:updated-at scenario))}
         options    (if (some? raster-resolution)
                      (assoc options :raster-resolution raster-resolution)
                      options)]
     #_(coverage/destroy-context (:coverage engine) context-id)
     (coverage/setup-context (:coverage engine) context-id options)
     context-id)))

;; Raster specialty functions

;; FIXME: move this to the file-store component
(defn- scenario-raster-work-path
  [project scenario]
  (str "data/scenarios/" (:id project) "/" (:id scenario) "/resized-raster.tif"))

(def suggest-max-pixels
  "Maximum number of pixels for rasters used for running the suggest location
  algorithms"
  (* 1024 1024))

(defn- prep-raster-for-search
  [engine project scenario]
  (debug (str "Resizing raster for suggestions algorithm in scenario " (:id scenario)))
  (let [scenario-raster-name (:raster scenario)
        scenario-raster-path (common/scenario-raster-full-path scenario-raster-name)
        scenario-raster      (raster/read-raster-without-data scenario-raster-path)
        scale-factor         (common/compute-down-scaling-factor scenario-raster suggest-max-pixels)
        resized-raster-path  (scenario-raster-work-path project scenario)
        resized-raster       (common/resize-raster (:runner engine) scenario-raster resized-raster-path scale-factor)]
    (debug (str "Resized raster is " (:xsize resized-raster) "x" (:ysize resized-raster)))

    (let [resized-raster    (raster/read-raster (:file-path resized-raster))
                                        ; re-read raster with data this time
          raster-resolution (raster/raster-resolution resized-raster)
          quartiles         (demand/compute-population-quartiles resized-raster)
          cutoff            (nth quartiles 3)]
      (debug (str "Raster quartiles computed as " quartiles))
      {:raster        resized-raster
       :demand-cutoff cutoff
       :resize-factor (* scale-factor scale-factor)
       :context-id    (setup-context-for-suggestions! engine project scenario raster-resolution)})))

(defn- remove-demand-point!
  [raster point]
  (debug (str "Erasing point " (pr-str point) " from search space"))
  (aset (:data raster) (:index point) (:nodata raster)))

(defn- raster-find-optimal-location
  [engine {:keys [raster context-id iteration] :as run-data}]
  (let [p-max (demand/find-max-demand raster)]
    (if (and p-max (pos? (:value p-max)))
      (let [iter-id  [:iteration iteration]
            p        p-max
            location (assoc p :id iter-id)
            result   (coverage/resolve-single (:coverage engine) context-id location :raster)]
        (if (:resolved result)
          (let [p-coverage     (raster/read-raster (:raster-path result))
                demand-covered (demand/count-population-under-coverage raster p-coverage)]
            (demand/multiply-population-under-coverage! raster p-coverage 0.0)
            (remove-demand-point! raster p)
            {:location {:location (select-keys p [:lat :lon])
                        :coverage demand-covered}
             :run-data run-data})
          (do
            ;; coverage for point cannot be resolved; remove it from the set and continue
            (remove-demand-point! raster p-max)
            (recur engine run-data))))
      {:location nil
       :run-data run-data})))

(defmethod search-optimal-locations "raster"
  [engine project scenario]
  (let [run-data         (prep-raster-for-search engine project scenario)
        context-id       (:context-id run-data)
        resize-factor    (:resize-factor run-data)
        project-capacity (get-in project [:config :providers :capacity])]
    (let [limit                 5
          {:keys [suggestions]} (compute-suggestions engine raster-find-optimal-location run-data limit)
          suggestions           (->> suggestions
                                     (filter #(pos? (:coverage %)))
                                     (take limit))]
      (info (str "Found " (count suggestions) " locations"))
      #_(coverage/destroy-context (:coverage engine) context-id)
      (map (fn [sugg]
             (let [scaled-coverage   (* resize-factor (:coverage sugg))
                   required-capacity (Math/ceil (/ scaled-coverage project-capacity))]
               (assoc sugg
                      :coverage scaled-coverage
                      :action-capacity required-capacity)))
           suggestions))))

;; Points specialty functions
;;

(defn- prep-points-for-search
  [engine project scenario]
  (let [sources (->> (get-in scenario [:sources-data])
                     (map #(select-keys % [:id :lat :lon :quantity]))
                     (map #(rename-keys % {:quantity :value}))
                     (sort-by :value >))]
    {:sources       sources
     :source-set-id (:source-set-id project)
     :context-id    (setup-context-for-suggestions! engine project scenario)}))

(defn- remove-sources-covered
  [sources ids]
  (let [ids (set ids)]
    (remove #(contains? ids (:id %)) sources)))

(defn- sum-sources-covered
  [sources ids]
  (let [ids (set ids)]
    (->> sources
         (filter #(contains? ids (:id %)))
         (map :value)
         (reduce + 0))))

(defn- points-find-optimal-location
  [engine run-data]
  (let [source-set-id (:source-set-id run-data)
        context-id    (:context-id run-data)
        sources       (:sources run-data)
        iteration     (:iteration run-data)
        p-max         (first sources)]
    (if (and p-max (pos? (:value p-max)))
      (let [iter-id  [:iteration iteration]
            p        p-max
            location (assoc p :id iter-id)
            result   (coverage/resolve-single (:coverage engine) context-id location [:sources-covered source-set-id])]
        (if (:resolved result)
          (let [sources-covered (:sources-covered result)
                demand-covered  (sum-sources-covered sources sources-covered)
                new-sources     (remove-sources-covered sources (conj sources-covered (:id p)))]
            {:location {:location (select-keys p [:lat :lon])
                        :coverage demand-covered}
             :run-data (assoc run-data :sources new-sources)})
          (do
            ;; coverage for point cannot be resolved; remove it from the set and continue
            (let [new-sources (remove-sources-covered sources [(:id p)])]
              (recur engine (assoc run-data :sources new-sources))))))
      {:location nil
       :run-data run-data})))

(defmethod search-optimal-locations "points"
  [engine project scenario]
  (let [run-data         (prep-points-for-search engine project scenario)
        context-id       (:context-id run-data)
        project-capacity (get-in project [:config :providers :capacity])]
    (let [limit                 5
          {:keys [suggestions]} (compute-suggestions engine points-find-optimal-location run-data limit)
          suggestions           (->> suggestions
                                     (filter #(pos? (:coverage %)))
                                     (take limit))]
      (info (str "Found " (count suggestions) " locations"))
      #_(coverage/destroy-context (:coverage engine) context-id)
      (map (fn [sugg]
             (let [required-capacity (Math/ceil (/ (:coverage sugg) project-capacity))]
               (assoc sugg :action-capacity required-capacity)))
           suggestions))))


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


;; REPL testing ===============================================================
;;

(comment

  (let [project  (planwise.boundary.projects2/get-project (dev/projects2) 2)
        scenario (planwise.boundary.scenarios/get-scenario (dev/scenarios) 18)]
    (search-optimal-locations (dev/engine) project scenario))

  (coverage/resolve-single (dev/coverage) [:suggestions 2] {:id 2 :lon 18.77 :lat 4.4} :raster)

  (first (:sources-data (planwise.boundary.scenarios/get-scenario (dev/scenarios) 33)))

  nil)
