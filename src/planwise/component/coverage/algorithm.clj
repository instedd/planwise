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