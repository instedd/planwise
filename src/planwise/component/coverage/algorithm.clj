(ns planwise.component.coverage.algorithm
  (:require [planwise.engine.raster :as raster]
            [planwise.component.coverage :as coverage])
  (:import [java.lang.Math]
           [org.gdal.gdalconst gdalconst]))

;Auxiliar functions
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

(defn unique-random-numbers [n bound]
  (loop [num-set (set (take n (repeatedly #(rand-int bound))))]
    (let [size  (count num-set)]
      (if (= size n)
        num-set
        (recur (clojure.set/union num-set  (set (take (- n size) (repeatedly #(rand-int n))))))))))

(defn total-sum
  [vector data]
  (reduce + (map #(aget data %) vector)))

(defn format*
  [[idx w :as val] {:keys [xsize ysize geotransform]}]
  (let [[lon lat] (pixel->coord geotransform (get-pixel idx xsize ysize))]
    [lon lat w]))

;(get-centroid [[0 3 40] [0 5 1]])  3.0487804
(defn get-centroid
  [set-points]
  (let [[r0 r1 total-w]  (reduce (fn [[r0 r1 partial-w] [l0 l1 w]]
                                    [(+ r0 (* w l0)) (+ r1 (* w l1)) (+ partial-w w)]) [0 0 0] set-points)]
  (map #(/ % total-w) [r0 r1])))

;  (cond (empty? set-prioriy) (filter (fn [[idx val]] (> val b2)) indexed-data)
;        (> (count set-priority) 150000) [(take n set-priority) (drop n set-priority)])

;GA for demand points
(defn create-initial-population
    [data [ _ b0 b1 b2 _ :as demand-quartiles]]
  (let [indexed-data (map-indexed vector (vec data))
        initial-set  (sort-by last > (filter (fn [[idx val]] (> val b2)) indexed-data))]
    initial-set))

(defn distance-from
  [[l0 l1 _]]
  (fn [[lon lat _]] (+ (Math/abs (- lon l0)) (Math/abs (- lat l1)))))

(defn distance-score
  [selected other raster]
  (let [f #(format* % )])
  ((distance-from (format* selected raster)) (format* other raster)))

(defn find-mates
  [n priorities others]
  (let [m (* 0.05 n)]
    (if (or (= n 0) (> (rand) 0.2718))
      (rand-nth others)
      (random-sample 0.95 (take (+ n m) list)))))

;nota mental: (cost-fn [idx]...)
;selected-pair [[idx val][idx val]]
;n-mates (rand-int (range 1 (quot (count population) 2)))

(defn selection
  [raster population others]
  (let [multipair-fn    (fn [selected] (find-mates (rand-int 500) (drop 1 (sort-by #(distance-score selected % raster) population))others))
        priorities-mate (map multipair-fn population)]
    (map vector population priorities-mate)))

(defn reproduction
  [selection]
  (map get-centroid selection))

(defn update-visits
  [locations vector]
  (filter (fn [[idx _]] (not (.contains vector idx))) locations))

(defn update-rest
  [rest next]
  (filter #(not= next %) rest))

(defn select-locations
  [coverage-fn locations ammount]

  (loop [selected []
         rest locations
         available locations
         n 0]

    (if (or (< n ammount) (empty? locations))

      (let [next (first locations)
            {:keys [count vector]} (coverage-fn next)
            rest*       (update-rest rest next)
            locations* (update-visits locations vector)]
        (recur (conj selected next) locations* rest* (inc n)))


      selected)))

(defn bound
  [locations coverage-fn]
  (let [vals (map coverage-fn locations)
        total (count locations)
        [p0 p1 :as partial-bounds] (/ (reduce  (fn [[pl1 pl2] {:keys [bounds]}]
                                                  (let [[_ _ l1 l2] bounds] [(+ pl1 l1) (+ pl2 l2)])) [0 0] vals) total)
        mean-coverage (/ (reduce + (map :count vals)) total)]
      (fn [cov-area count] (> (/ (/ mean-coverage (* p0 p1)) (/ count cov-area)) 0.95))))


(defn evolve
  [{:keys [raster cost-fn demand-quartiles]}]
  (let [{:keys [data]}  raster
        locations       (create-initial-population data demand-quartiles)
        pre-selected    (select-locations cost-fn locations 1000)
        rest            (reduce update-rest locations pre-selected)]

    (loop [locations pre-selected
           rest      rest
           k 0]

          (println "prov answers" (take 10 locations))

          (if (= k 100)

            (take 10 locations)

            (let [multi-parents   (selection raster locations rest)
                  next-generation (reproduction multi-parents)
                  locations       (sort-by #(-> % cost-fn :count) > (conj locations next-generation))
                  condition       (> (count locations) 10000)]

               (recur (if condition (take 10000 locations) locations)
                      (if condition (conj rest (drop 10000 locations) rest))
                      (inc k)))))))
