(ns planwise.engine.suggestions
  (:require [planwise.boundary.providers-set :as providers-set]
            [planwise.boundary.sources :as sources-set]
            [planwise.component.coverage.greedy-search :as gs]
            [planwise.boundary.coverage :as coverage]
            [planwise.engine.raster :as raster]
            [planwise.component.coverage.rasterize :as rasterize]
            [planwise.engine.demand :as demand]
            [planwise.util.files :as files]
            [taoensso.timbre :as timbre]))

(timbre/refer-timbre)

(defn count-under-geometry
  [engine polygon {:keys [raster original-sources source-set-id geom-set]}]
  (if raster
    (let [coverage (raster/create-raster (rasterize/rasterize polygon))]
      (demand/count-population-under-coverage raster coverage))
    (let [ids (set (map :id (sources-set/list-sources-under-coverage (:sources-set engine) source-set-id polygon)))]
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
  [engine {:keys [criteria region-id project-capacity]} {:keys [sources-data search-path] :as source} {:keys [coord get-avg get-update]}]
  (let [criteria              (if sources-data criteria (merge criteria {:raster search-path}))
        [lon lat :as coord]   coord
        polygon               (coverage/compute-coverage (:coverage engine) {:lat lat :lon lon} criteria)
        population-reacheable (count-under-geometry engine polygon source)
        info   {:coverage population-reacheable
                :required-capacity (/ population-reacheable project-capacity)
                :coverage-geom (:geom (coverage/geometry-intersected-with-project-region (:coverage engine) polygon region-id))
                :location {:lat lat :lon lon}}]
    (cond get-avg {:max (coverage/get-max-distance-from-geometry (:coverage engine) polygon)}
          get-update {:location-info info
                      :updated-demand (get-demand-source-updated engine source polygon get-update)}
          :other info)))

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
    (let [bound    (when provider-set-id (:avg-max (providers-set/get-radius-from-computed-coverage (:providers-set engine) criteria provider-set-id)))
          locations (gs/greedy-search 10 source coverage-fn demand-quartiles {:bound bound :n 20})]
      locations)))

(comment
  ;REPL testing
  ;Correctnes of coverage

  (def raster (raster/read-raster "data/scenarios/44/initial-5903759294895159612.tif"))
  (def criteria {:algorithm :simple-buffer :distance 20})
  (def val 1072404)
  (def f (fn [val] (engine/get-coverage (:coverage engine) {:idx val} raster criteria)))
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

(comment
  (defn generate-raster-sample
    [coverage locations criteria]
    (let [kilifi-pop (raster/read-raster "data/kilifi.tif")
          new-pop    (raster/read-raster "data/cerozing.tif")
          dataset-fn (fn [loc] (let [polygon (coverage/compute-coverage coverage loc criteria)] (rasterize/rasterize polygon)))
          get-index  (fn [dataset] (vec (demand/get-coverage kilifi-pop (raster/create-raster dataset))))
          same-values (fn [set] (map (fn [i] [i (aget (:data kilifi-pop) i)]) set))
          set         (reduce into (mapv #(-> % dataset-fn get-index same-values) locations))
          new-data    (reduce (fn [new-data [idx val]] (assoc new-data idx val)) (vec (:data new-pop)) set)
          raster      (raster/create-raster-from-existing kilifi-pop (float-array new-data))]
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
