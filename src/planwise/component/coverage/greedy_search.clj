(ns planwise.component.coverage.greedy-search
  (:require [planwise.engine.raster :as raster]
            [clojure.core.memoize :as memoize]
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
  (let [[x0 xres _ y0 _ yres] (vec geotransform)
        coord-fn (fn [[x y]] [(+ x0 (* x xres)) (+ y0 (* y yres))])]
    (coord-fn pixels-vec)))

(defn coord->pixel
  [geotransform coord-vec]
  (let [[x0 xres _ y0 _ yres] (vec geotransform)
        pix-fn (fn [[lon lat]] [(Math/round (/ (- lon x0) xres)) (Math/round (/ (- lat y0) yres))])]
    (pix-fn coord-vec)))

(defn get-pixel
  [idx xsize]
  (let [height (quot idx xsize)
        width (mod idx xsize)]
    [width height]))

(defn get-geo
  [idx {:keys [xsize ysize geotransform]}]
  (pixel->coord geotransform (get-pixel idx xsize)))

(defn get-index
  [[lon lat] {:keys [xsize geotransform]}]
  (let [[x y :as pixel-vec] (coord->pixel geotransform [lon lat])]
    (+ (* y xsize) x)))

(defn *format
  [coord raster]
  (let [idx (get-index coord raster)]
    [idx (aget (:data raster) idx)]))

(defn format*
  [[idx w :as val] raster]
  (let [[lon lat] (get-geo idx raster)]
    [lon lat w]))

(defn get-centroid
  [set-points]
  (let [[r0 r1 total-w]  (reduce (fn [[r0 r1 partial-w] [l0 l1 w]]
                                   [(+ r0 (* w l0)) (+ r1 (* w l1)) (+ partial-w w)]) [0 0 0] set-points)]
    (if (pos? total-w)
      (map #(/ % total-w) [r0 r1])
      (first set-points))))

(defn get-geo-centroid
  [set-points]
  (let [total (count set-points)
        [r0 r1] (reduce (fn [[r0 r1] [l0 l1]] [(+ r0 l0) (+ r1 l1)]) [0 0] set-points)]
    (if (pos? total) (map #(/ % total) [r0 r1]) (first set-points))))

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

(defn frontier-fn
  [[idx _] raster radius eps]
  (let [coord (get-geo idx raster)]
    (fn [[other _]] (< (- (euclidean-distance coord (get-geo other raster)) radius) eps))))

;for comparing//get-neighbour
(defn get-maximal-neighbour
  [demand-point raster initial-set {:keys [avg-max] :as bounds}]
  (let [is-neighbour? (fn [p] (neighbour-fn p raster (/ avg-max 2)))
        group-A       (get (group-by (is-neighbour? demand-point) (drop 1 initial-set)) true)
        groups        (map (fn [e] (get (group-by (is-neighbour? e) initial-set) true)) group-A)
        group-weight-fn (fn [group] (reduce (fn [sum [_ val]] (when (pos? val) (+ sum val))) 0 group))
        weights         (map-indexed (fn [i e] {:val (group-weight-fn e) :group e}) groups)
        {:keys [group val]} (first (sort-by :val > weights))
        maximal       {:set group :total-weight val}]
    maximal))

(defn get-subgroups
  [raster demand step center radius]
  (let [is-neighbour? (fn [radius] (neighbour-fn center raster radius))
        initial-set   (get (group-by (is-neighbour? radius) demand) true)]
    (loop [set initial-set
           k 0
           subs []]
      (if (empty? set)
        subs
        (let [sep (group-by (is-neighbour? (* (inc k) step)) set)
              subs* (into subs [(get sep true)])]
          (recur (get sep false) (inc k) subs*))))))

(defn next-neigh
  ([raster demand center radius]
   (next-neigh raster demand center radius 0))
  ([raster demand center radius eps]
   (let [in-frontier? (fn [p] (frontier-fn p raster radius eps))
         frontier (get (group-by (in-frontier? center) demand) true)]
     (get-centroid (map #(format* % raster) frontier)))))

(defn get-neighbour
  [demand-point {:keys [data] :as raster} demand avg-max]
  (let [is-neighbour? (fn [p] (neighbour-fn p raster avg-max))]

    (when (some? (get (group-by (is-neighbour? demand-point) demand) true))

      (loop [sum 0
             radius avg-max
             [idx _ :as center] demand-point]

        (if (<= (- avg-max sum) 0)

          (let [sep (group-by (is-neighbour? center) demand)]
            (println "success!")
            [(get sep true) (get sep false)])

          (let [location    (next-neigh raster demand center radius)]
            (if (nil? location)
              (recur avg-max radius center)
              (let [index       (get-index location raster)
                    next-center [index (aget data index)]
                    next-radius (euclidean-distance (get-geo idx raster) location)
                    step        (- radius next-radius)]

                (if (and (> step 0) (pos? next-radius))
                  (recur (+ sum step) next-radius next-center)
                  (recur avg-max radius center))))))))))

(defn get-groups
  [from initial-set raster bounds]
  (loop [groups []
         from   (first initial-set)
         demand initial-set]

    (if (nil? from)

      groups

      (let [[group demand*] (get-neighbour from raster demand bounds)]
        (if (nil? group)
          (recur groups (take 1 demand) (drop 1 demand))
          (let [groups*         (into groups [group])
                from*           (first (sort-by second > demand*))]
            (recur groups* from* demand*)))))))

(defn greedy-search
  [{:keys [data] :as raster} coverage-fn demand-quartiles {:keys [n bounds]}]
  (let [[max & remain :as initial-set]   (get-demand data demand-quartiles)
        {:keys [avg-max] :as bounds}     (or bounds (mean-initial-data n initial-set coverage-fn))
        groups    (map (fn [group] (map #(get-geo (first %) raster) group)) (get-groups max initial-set raster (/ avg-max 2)))
        geo-cent  (map get-geo-centroid groups)]
        ;in-cache-vals (map second (memoize/snapshot coverage-fn))
    (sort-by :cov > (map #(coverage-fn % true) geo-cent))))

(defn catch-exc
  [function & params]
  (try
    (apply function params)
    (catch Exception e
      take 10 (:trace e))))

;Greedy Search
(comment
;gdalwarp -tr 0.0016666667 -0.0016666667 initial-8026027092040813847.tif half-res-initial-8026027092040813847.tif
  (require '[planwise.engine.raster :as raster])
  (require '[planwise.component.coverage.greedy-search :refer :all])
  (require '[planwise.component.engine :as engine])
  (require '[clojure.core.memoize :as memoize])
  (def engine (:planwise.component/engine system))
  (def raster (raster/read-raster "data/scenarios/44/initial-8026027092040813847.tif"))
  (def half-raster (raster/read-raster "data/scenarios/44/half-res-initial-8026027092040813847.tif"))
  (def ten-raster (raster/read-raster "data/scenarios/44/ten-res-initial-8026027092040813847.tif"))
  (def hun-raster (raster/read-raster "data/scenarios/44/hun-res-initial-8026027092040813847.tif"))

  (def demand-quartiles  [0.000015325084 0.00689443 0.09416369 0.8036073 230.33006])

  (def demand (planwise.component.coverage.greedy-search/get-demand (:data raster) demand-quartiles))
  (def demand-hr (planwise.component.coverage.greedy-search/get-demand (:data half-raster) demand-quartiles))

  (def criteria {:algorithm :simple-buffer :distance 20})
  (def criteria {:algorithm :pgrouting-alpha :driving-time 60})
  (def criteria {:algorithm :walking-friction :walking-time 120})

  (def coverage-fn
    (memoize/lu (fn ([val] (engine/coverage-fn (:coverage engine) val raster criteria)))))

  (def cost-fn (fn ([[idx _]] (catch-exc coverage-fn {:idx idx :res (float (/ 1 1200))}))
                 ([coord condition] (catch-exc coverage-fn {:coord coord :res (float (/ 1 1200))}))))

  ;changing resolution
  (def ten-raster (raster/read-raster "data/scenarios/44/ten-res-initial-8026027092040813847.tif"))

  (def cov-fn
    (memoize/lu (fn ([val] (engine/coverage-fn (:coverage engine) val ten-raster criteria)))))

  (def costit-fn* (fn ([[idx _]]
                       (catch-exc cov-fn {:idx idx :res (float (/ 1 120))}))
                    ([coord condition]
                     (catch-exc cov-fn {:coord coord :res (float (/ 1 120))}))))

  (def bounds (mean-initial-data 100 demand-hr cost-fn*)))
