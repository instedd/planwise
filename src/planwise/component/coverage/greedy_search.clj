(ns planwise.component.coverage.greedy-search
  (:require [planwise.engine.raster :as raster]
            [clojure.core.memoize :as memoize]
            [planwise.component.coverage :as coverage]
            [planwise.util.exceptions :refer [catch-exc]])
  (:import [java.lang.Math]
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
  [{:keys [raster sources-data]} [_ b0 b1 b2 _ :as demand-quartiles]]
  (if raster
    (let [indexed-data (map-indexed vector (vec (:data raster)))
          initial-set  (sort-by last > (filter (fn [[_ val]] (> val b2)) indexed-data))]
      (mapv (fn [[idx val]] (conj (get-geo idx raster) val)) initial-set))

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
   (next-neighbour demand center radius (/ radius 100000)))
  ([demand center radius eps]
   (let [in-frontier? (fn [p] (neighbour-fn p radius))
         set      (group-by (in-frontier? center) demand)
         frontier (get set true)]
     (get-centroid frontier))))

(defn update-demand
  [coverage-fn source demand-point demand avg-max]

  (let [get-value-fn  (if (vector? source)
                        (fn [location] (last (filter #(= (drop-last %) location) source)))
                        (fn [location] (aget (:data source) (get-index location source))))
        is-neighbour? (memoize (fn [p r] (neighbour-fn p r)))
        interior      (filter (is-neighbour? demand-point avg-max) demand)]

    (if (some? interior)

      (loop [sum 0
             radius avg-max
             [lon lat _ :as center] demand-point
             interior interior]

        (if (<= (- avg-max sum) 0)

          (let [group    (filter (is-neighbour? center avg-max) demand)
                geo-cent (get-geo-centroid group)
                {:keys [location-info updated-demand]} (coverage-fn geo-cent {:get-update demand})]
            [location-info updated-demand])

          (let [location (next-neighbour interior center radius)]

            (if (nil? location)

              (recur avg-max radius center demand)

              (let [value       (get-value-fn location)
                    next-center (conj (vec location) value)
                    next-radius (euclidean-distance [lon lat] location)
                    step        (- radius next-radius)]

                (if (and (> step 0) (pos? next-radius))
                  (recur (+ sum step) next-radius next-center (get (group-by (is-neighbour? next-center next-radius) interior) true))
                  (recur avg-max radius center interior)))))))

      (let [demand  (if (vector? demand) demand [demand])
            {:keys [location-info updated-demand]} (coverage-fn (drop-last demand-point) {:get-update demand})]
        [location-info updated-demand]))))

(defn above-avg
  [point set bound locations]
  (let [locations (map (fn [{:keys [location]}] [(:lat location) (:lon location)]) locations)
        greater-than? #(>= (euclidean-distance (drop-last point) % (* 2 bound))
        condition (empty? (remove true? greater-than? locations))]
  (loop [from nil
         set set]
    (if (and (nil? from) (some? set))
      (let [[lon lat _ :as from*] (first set)
            from (when (and (greater-than? from)condition) from*)]
      (recur from (drop 1 set)))
      from)))))


(defn get-locations
  [coverage-fn source from initial-set bound]

  (loop [times 0
         locations []
         from   (first initial-set)
         demand (drop 1 initial-set)]

    (println "locations" (count locations))

    (if (or (nil? demand) (nil? from) (= times 10))

      locations

      (let [[location demand*]      (update-demand coverage-fn source from demand bound)
            [from* & demand* :as set]       (sort-by last > demand*)
            from* (if (vector? source) from* (above-avg from set bound locations))]
        (println "from*" from* )
        (println "dem" (count demand*))
        (recur (inc times) (conj locations location) from* demand* )))))

;TODO check mean-initial-data (or bounds (mean-initial-data n initial-set coverage-fn))
(defn greedy-search
  [sample {:keys [raster sources-data] :as source} coverage-fn demand-quartiles {:keys [n bounds]}]
  (println sources-data)
  (let [[max & remain :as initial-set]   (if raster (get-demand {:raster raster} demand-quartiles) sources-data)
        {:keys [avg-max] :as bounds}     (or bounds (mean-initial-data n initial-set coverage-fn))
        locations (get-locations coverage-fn (or raster sources-data) max initial-set (/ avg-max 2))]
    (println (map :location locations))
    (if (empty? locations)
      (throw (IllegalArgumentException. "Demand can't be reached."))
      (take sample (sort-by :coverage > locations)))))
