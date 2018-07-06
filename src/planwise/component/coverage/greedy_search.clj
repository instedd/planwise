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
  [n {:keys [data] :as raster} demand-quartiles coverage-fn]
  (let [demand  (get-demand data demand-quartiles)
        locations (take n (random-sample 0.8 (get-demand data demand-quartiles)))
        [total-cov total-max] (reduce (fn [[tc tm] location]
                                        (let [{:keys [cov max]} (or (coverage-fn location) {:cov 0 :max 0})]
                                          [(+ tc cov) (+ tm max)])) [0 0] locations)]
    {:avg-cov (float (/ total-cov n))
     :avg-max (/ total-max n)}))

(defn neighbour-fn
  [[idx _] raster bound]
  (let [coord (get-geo idx raster)]
    (fn [[other _]] (< (euclidean-distance coord (get-geo other raster)) bound))))

(defn group-by-nearness
  [raster initial-set {:keys [avg-cov avg-max]}]

  (loop [loc (first initial-set)
         groups {}
         rest initial-set]

    (println "groups:" (last (sort (keys groups))))

    (if (empty? rest)
      groups
      (let [is-neighbour? (neighbour-fn loc raster avg-max)
            classif (group-by is-neighbour? rest)
            new-g   (get classif true)
            groups* (if (nil? new-g) groups (assoc groups (-> groups count str keyword) new-g))]

        (recur (first rest)
               groups*
               (get classif false))))))

(defn get-fitted-location
  [raster demand-quartiles coverage-fn]
  (let [initial-set (get-demand (:data raster) demand-quartiles)
        bounds      (mean-initial-data 100 raster demand-quartiles coverage-fn)
        groups      (group-by-nearness raster initial-set bounds)
        centroids   (map get-centroid (map (fn [set] (map #(format* % raster) set)) (vals groups)))
        evaluated-cen (map (fn [loc] (coverage-fn loc true)) centroids)]
    (sort-by :cov > evaluated-cen)))

;Greedy Search
(comment
  (require '[planwise.engine.raster :as raster])
  (require '[planwise.component.engine :as engine])
  (def engine (:planwise.component/engine system))
  (def raster (raster/read-raster "data/scenarios/44/initial-5903759294895159612.tif"))
  (def demand-quartiles  [0 0 0.001840375 0.018976588 12.925134])
  (def initial-set (m/get-demand (:data raster) demand-quartiles))
  (def criteria {:algorithm :simple-buffer :distance 20})
  (def cost-fn
    (memoize (fn ([[idx _]] (engine/coverage-fn (:coverage engine) {:idx idx} raster criteria))
               ([coord condition] (engine/coverage-fn (:coverage engine) {:coord coord} raster criteria)))))

  (planwise.component.coverage.greedy-search/get-fitted-location raster demand-quartiles cost-fn))
;;"Elapsed time: 24647.075663 msecs"
  ;({:cov 25682, :loc (39.90273631089216 -3.132163897913722)}
  ;{:cov 20360, :loc (40.051918846603954 -3.139158249127601)}
  ;{:cov 17806, :loc (39.803771441732245 -2.9993490926084343)}
  ;{:cov 17648, :loc (39.724566152910214 -3.788363721191471)}

