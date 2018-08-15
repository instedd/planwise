(ns planwise.component.coverage.greedy-search
  (:require [planwise.engine.raster :as raster]
            [planwise.util.files :as files]
            [clojure.core.memoize :as memoize]
            [planwise.component.coverage :as coverage])
            ;[planwise.util.exceptions :refer [catch-exc]])
  (:import [java.lang.Math]
           [planwise.engine Algorithm]
           [org.gdal.gdalconst gdalconst]))

;; Idea of algorithm:
 ;; i.   Fixed radius according to coverage algorithm:
          ;From computed providers
          ;or from random sampling of last quartile of demand.
 ;; ii.  Sort demand points by weight to set priorities.
 ;; iii. Given a high demand point, look for good enough neighbourhood (of radius obtained in (i)) in which is contained.
 ;; iv.  Once grouped, calculate centroide of group and apply coverage function."

;---------------------------------------------------------------------------------------------------------
;Auxiliar functions

(defn euclidean-distance-squared
  [[a0 a1] [b0 b1]]
  (+ (* (- b0 a0) (- b0 a0)) (* (- b1 a1) (- b1 a1))))

(defn euclidean-distance
  [a b]
  (Math/sqrt (euclidean-distance-squared a b)))

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
  (let [y (quot idx xsize)
        x (mod idx xsize)]
    [x y]))

(defn get-geo
  [idx {:keys [xsize ysize geotransform]}]
  (pixel->coord geotransform (get-pixel idx xsize)))

(defn get-index
  [[lon lat] {:keys [xsize geotransform]}]
  (let [[x y :as pixel-vec] (coord->pixel geotransform [lon lat])]
    (+ (* y xsize) x)))

(defn get-weighted-centroid
  [set-points]
  (let [[r0 r1 total-w]  (reduce (fn [[r0 r1 partial-w] [l0 l1 w]]
                                   [(+ r0 (* w l0)) (+ r1 (* w l1)) (+ partial-w w)]) [0 0 0] set-points)]
    (if (pos? total-w)
      (map #(/ % total-w) [r0 r1])
      (first set-points))))

(defn get-centroid
  [set-points]
  (let [total (count set-points)
        [r0 r1] (reduce (fn [[r0 r1] [l0 l1]] [(+ r0 l0) (+ r1 l1)]) [0 0] set-points)]
    (if (pos? total) (map #(/ % total) [r0 r1]) (first set-points))))

(defn fast-raster-saturated-locations
  [raster cutoff]
  (let [{:keys [data nodata xsize geotransform]} raster
        saturated-indices (Algorithm/filterAndSortIndices data nodata cutoff)]
    (Algorithm/locateIndices data saturated-indices xsize geotransform)))

;; old implementation; fails with OOM for big rasters
(defn slow-raster-saturated-locations
  [raster cutoff]
  (let [indexed-data (map-indexed vector (vec (:data raster)))
        initial-set  (sort-by last > (filter (fn [[_ val]] (> val cutoff)) indexed-data))]
    (mapv (fn [[idx val]] (conj (get-geo idx raster) val)) initial-set)))

(defn get-saturated-locations
  [{:keys [raster sources-data]} [_ b0 b1 b2 _ :as demand-quartiles]]
  (if raster
    (fast-raster-saturated-locations raster b2)
    (mapv (fn [{:keys [lon lat quantity]}] [lon lat quantity]) (sort-by :quantity > sources-data))))

(defn mean-initial-data
  [n demand coverage-fn]
  (let [locations (take n (random-sample 0.8 demand))
        total-max (reduce (fn [tm location]
                            (let [{:keys [max]} (or (coverage-fn (drop-last location) {:get-avg true}) {:cov 0 :max 0})]
                              (+ tm max))) 0 locations)]
    {:avg-max (/ total-max n)}))

(defn neighbour-fn
  ([coord bound]
   (fn [[lon lat _]] (< (euclidean-distance (drop-last coord) [lon lat]) bound)))
  ([coord radius eps]
   (fn [[lon lat _]] (< (- (euclidean-distance (drop-last coord) [lon lat]) radius) eps))))

(defn next-neighbour
  ([demand center radius]
   (next-neighbour demand center radius (/ radius 1000)))
  ([demand center radius eps]
   (let [in-frontier? (fn [p] (neighbour-fn p radius))
         frontier     (filter (in-frontier? center) demand)]
     (get-weighted-centroid frontier))))

(defn update-demand
  [coverage-fn {:keys [sources-data raster]} demand-point demand avg-max]
  {:pre [(not (nil? demand))]}

  (let [source (or raster sources-data)
        get-value-fn  (if raster
                        (fn [location] (aget (:data source) (get-index location source)))
                        (fn [location] (last (filter #(= (drop-last %) location) source))))
        is-neighbour? (memoize (fn [p r] (neighbour-fn p r)))
        interior      (filter (is-neighbour? demand-point avg-max) demand)]

    (if (empty? interior)

      (let [division (group-by (is-neighbour? demand-point avg-max) demand)
            {:keys [location-info updated-demand]} (coverage-fn (drop-last demand-point) {:get-update demand})
            demand* (when raster (vec (clojure.set/intersection (set (get division false)) (set updated-demand))))]
        [location-info updated-demand])

      (loop [sum 0
             radius avg-max
             [lon lat _ :as center] demand-point
             interior interior]

        (if (<= (- avg-max sum) 0)

          (let [division (group-by (is-neighbour? center avg-max) demand)
                geo-cent (get-centroid (get division true))
                {:keys [location-info updated-demand]} (coverage-fn geo-cent {:get-update demand})
                demand*  (when raster (vec (clojure.set/intersection (set (get division false)) (set updated-demand))))]
            [location-info (or demand* updated-demand)])

          (let [location (next-neighbour interior center radius)]

            (if (nil? location)

              (recur avg-max radius center demand)

              (let [value       (get-value-fn location)
                    next-center (conj (vec location) value)
                    next-radius (euclidean-distance [lon lat] location)
                    step        (- radius next-radius)]

                (if (and (> step 0) (pos? next-radius))
                  (recur (+ sum step) next-radius next-center (filter (is-neighbour? next-center next-radius) demand))
                  (recur avg-max radius center interior))))))))))

(defn get-locations
  [coverage-fn source from initial-set bound sample]
  (loop [times 0
         locations []
         from   (first initial-set)
         demand (rest initial-set)]

    (if (or (nil? demand) (nil? from) (= times sample))
      (remove #(zero? (:coverage %)) locations)
      (let [[location demand*]        (update-demand coverage-fn source from demand bound)
            [from* & demand* :as set] (sort-by last > demand*)]
        (recur (inc times) (conj locations location) from* demand*)))))

(defn greedy-search
  [sample {:keys [raster  search-path sources-data] :as source} coverage-fn demand-quartiles {:keys [n bounds]}]
  (let [[max & remain :as initial-set]   (if raster (get-saturated-locations {:raster raster} demand-quartiles) sources-data)
        {:keys [avg-max] :as bounds}     (or bounds (mean-initial-data n initial-set coverage-fn))
        locations (get-locations coverage-fn source max initial-set (/ avg-max 2) sample)]

    (when search-path (clojure.java.io/delete-file search-path true))
    (if (empty? locations)
      (throw (IllegalArgumentException. "Demand can't be reached."))
      (sort-by :coverage > locations))))
