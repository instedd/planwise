(ns planwise.component.coverage.greedy-search
  (:require [planwise.engine.raster :as raster]
            [planwise.component.coverage :as coverage])
  (:import [java.lang.Math]
           [org.gdal.gdalconst gdalconst]))

;; Idea of algorithm:
 ;; i.   From random sampling of last quartile of demand estimate coverage and maximus distance covered from selected location.
 ;; ii.  Sort demand points by weight to set priorities.
 ;; iii. Given a high demand point, associate other demand points groupped by nearness considering bound in (i)
 ;; iv.  Once grouped, calculate centroide of group and apply coverage function.
 ;; ivi. Eval fitness of solution, repeat procedure."

;Auxiliar functions
(defn euclidean-distance [a b]
  (Math/pow  (reduce + (map #(-> (- %1 %2) (Math/pow 2)) a b))
             (/ 1 2)))

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

(defn format*
  [[idx w :as val] {:keys [xsize ysize geotransform]}]
  (let [[lon lat] (pixel->coord geotransform (get-pixel idx xsize ysize))]
    [lon lat w]))

(defn get-geo
  [idx {:keys [xsize ysize geotransform]}]
  (pixel->coord geotransform (get-pixel idx xsize ysize)))

(defn get-centroid
  [set-points]
  (let [[r0 r1 total-w]  (reduce (fn [[r0 r1 partial-w] [l0 l1 w]]
                                   [(+ r0 (* w l0)) (+ r1 (* w l1)) (+ partial-w w)]) [0 0 0] set-points)]
    (if (pos? total-w)
      (map #(/ % total-w) [r0 r1])
      (first set-points))))

;Initializing search
(defn get-demand
  [data [_ b0 b1 b2 _ :as demand-quartiles]]
  (let [indexed-data (map-indexed vector (vec data))
        initial-set  (sort-by last > (filter (fn [[idx val]] (> val b2)) indexed-data))]
    initial-set))

;TODO n to guarantee reliable result
;Or take coverage-criteria into consideration
(defn mean-initial-data
  [n demand coverage-fn]
  (let [locations (take n (random-sample 0.8 demand))
        [total-cov total-max] (reduce (fn [[tc tm] location]
                                        (let [{:keys [cov max]} (or (coverage-fn location) {:cov 0 :max 0})]
                                          [(+ tc cov) (+ tm max)])) [0 0] locations)]
    {:avg-cov (float (/ total-cov n))
     :avg-max (/ total-max n)}))

(defn neighbour-fn
  [[idx _] raster bound]
  (let [coord (get-geo idx raster)]
    (fn [[other _]] (< (euclidean-distance coord (get-geo other raster)) bound))))

;for pg-routing nearness may be determined for way nodes in routes
      ;coverage-criteria should be also taken into consideration when finding neighbours

(defn group-by-nearness
  [raster initial-set {:keys [avg-cov avg-max]} from]

  (loop [loc from
         groups {}
         rest initial-set]

    (if (empty? rest)
      groups
      (let [is-neighbour? (neighbour-fn loc raster avg-max)
            classif (group-by is-neighbour? rest)
            new-g   (get classif true)
            groups* (if (nil? new-g) groups (assoc groups (-> groups count str keyword) new-g))]

        (recur (first rest)
               groups*
               (get classif false))))))

(defn not-covered-yet
  [best-loc new]
  (if (empty? best-loc)
    true
    (let [dst-fn #(euclidean-distance (:loc new) %)
          check-fn (fn [l] (> (:max l) (dst-fn (:loc l))))]
      (.contains (map check-fn best-loc) false))))

(defn update-best
  [best-loc others]
  (let [others (take 10 (take-while #(not-covered-yet best-loc %) others))]
    (if (empty? best-loc)
      others
      (take 10 (sort-by :cov > (into best-loc others))))))

;k may be replaced by a fitness function with criteria of deciding when a solution is optimal
  ;example: fitness-fn (fn [bl] (if (and (not (empty? bl)) (pos? avg-cov)) (< (/ (:cov (first bl)) avg-cov) 0.95) true))

(defn get-fitted-location
  [raster demand-quartiles coverage-fn times]
  (let [initial-set (get-demand (:data raster) demand-quartiles)
        {:keys [avg-cov] :as bounds} (mean-initial-data 100 initial-set coverage-fn)
        group-fn  (fn [from] (group-by-nearness raster initial-set bounds from))]

    (loop [k 0
           best-loc []]
      (println "k: " k)

      (if (< k times)

        (let [groups        (group-fn (rand-nth initial-set))
              centroids     (map get-centroid (map (fn [set] (map #(format* % raster) set)) (vals groups)))
              evaluated-cen (map (fn [loc] (coverage-fn loc true)) centroids)]

          (recur (inc k) (update-best best-loc (sort-by :cov > evaluated-cen))))

        best-loc))))

(defn brute-force
  [set-points coverage-fn]
  (map coverage-fn set-points))


;Greedy Search
(comment
  (require '[planwise.engine.raster :as raster])
  (require '[planwise.component.engine :as engine])
  (def engine (:planwise.component/engine system))
  (def raster (raster/read-raster "data/scenarios/44/initial-5903759294895159612.tif"))
  (def demand-quartiles  [0 0 0.001840375 0.018976588 12.925134])
  (def initial-set (get-demand (:data raster) demand-quartiles))
  (def criteria {:algorithm :simple-buffer :distance 20})
  (def cost-fn
    (memoize (fn ([[idx _]] (engine/coverage-fn (:coverage engine) {:idx idx} raster criteria))
               ([coord condition] (engine/coverage-fn (:coverage engine) {:coord coord} raster criteria)))))
  (planwise.component.coverage.greedy-search/get-fitted-location raster demand-quartiles cost-fn 100))

;;Comparing against Brute-force taking 100 first initial-demand points
  ;{:cov 11803, :loc [40.15343646666667 -2.8615743]}
  ;{:cov 11802, :loc [40.15343646666667 -2.8624076333333335]}
  ;{:cov 11800, :loc [40.15343646666667 -2.863240966666667]}
  ;{:cov 11799, :loc [40.15343646666667 -2.860740966666667]}
  ;{:cov 11797, :loc [40.15343646666667 -2.8640743000000004]}

;;Algorithm with no iterations
;;"Elapsed time: 24647.075663 msecs"
  ;{:cov 25682, :loc (39.90273631089216 -3.132163897913722)}
  ;{:cov 20360, :loc (40.051918846603954 -3.139158249127601)}
  ;{:cov 17806, :loc (39.803771441732245 -2.9993490926084343)}
  ;{:cov 17648, :loc (39.724566152910214 -3.788363721191471)}

;;Algortihm with iterations
            ;small numbers are being tested because of time-cost
            ;improvements may be considered if changing pixel resolution

;Not filtering when posible solution covered by best-loc
;;10 iterations - respecting weight priorities
;;"Elapsed time: 168517.230272 msecs" ~ no improvements
 ;{:cov 25682, :loc (39.90273631089216 -3.132163897913722)}
 ;{:cov 20360, :loc (40.051918846603954 -3.139158249127601)}
 ;{:cov 17806, :loc (39.803771441732245 -2.9993490926084343)}
 ;{:cov 17648, :loc (39.724566152910214 -3.788363721191471)}

;;10 iterations - randomly from demand-points
;{:cov 26380, :loc (39.91979778570984 -3.116269705483849)}
;{:cov 25682, :loc (39.90273631089216 -3.132163897913722)}
;{:cov 20360, :loc (40.051918846603954 -3.139158249127601)}
;{:cov 17806, :loc (39.803771441732245 -2.9993490926084343)}
;{:cov 17648, :loc (39.724566152910214 -3.788363721191471)}

;;Filtering when solution covered by best-loc ~ Notably improvements
;;10 iterations - randomly from demand-points
;{:cov 30197, :loc (39.95518910802926 -3.0263284639155796)}
;{:cov 28909, :loc (39.898564909971064 -3.023697023484523)}
;{:cov 26721, :loc (39.883311621106024 -3.098487588873633)}
;{:cov 25682, :loc (39.90273631089216 -3.132163897913722)}
