(ns planwise.engine.demand
  (:require [clojure.spec.alpha :as s]
            [planwise.engine.raster :as raster])
  (:import [planwise.engine Algorithm]))

(defn count-population
  [population]
  (let [{:keys [data nodata]} population]
    (Algorithm/countPopulation data nodata)))

(defn count-population-under-coverage
  "Count population pixel values under coverage"
  [population coverage]
  (let [{src-bounds :src dst-bounds :dst}       (raster/clipped-coordinates population coverage)
        {src-buffer :data src-nodata :nodata}   coverage
        {dst-buffer :data dst-nodata :nodata}   population
        [dst-left dst-top dst-right dst-bottom] dst-bounds
        [src-left src-top _ _]                  src-bounds
        dst-stride                              (:xsize population)
        src-stride                              (:xsize coverage)]
    (Algorithm/countPopulationUnderCoverage dst-buffer dst-stride dst-nodata
                                            src-buffer src-stride src-nodata
                                            dst-left dst-top dst-right dst-bottom
                                            src-left src-top)))

(defn multiply-population!
  [population factor]
  (let [{:keys [data nodata]} population]
    (Algorithm/multiplyPopulation data nodata factor)))

(defn multiply-population-under-coverage!
  "Multiply each population pixel value by factor if under coverage"
  [population coverage factor]
  (let [{src-bounds :src dst-bounds :dst}       (raster/clipped-coordinates population coverage)
        {src-buffer :data src-nodata :nodata}   coverage
        {dst-buffer :data dst-nodata :nodata}   population
        [dst-left dst-top dst-right dst-bottom] dst-bounds
        [src-left src-top _ _]                  src-bounds
        dst-stride                              (:xsize population)
        src-stride                              (:xsize coverage)]
    (Algorithm/multiplyPopulationUnderCoverage dst-buffer dst-stride dst-nodata
                                               src-buffer src-stride src-nodata
                                               factor
                                               dst-left dst-top dst-right dst-bottom
                                               src-left src-top)))

(comment
  (def raster1 (raster/read-raster "data/populations/data/17/42.tif"))
  (def raster2 (raster/read-raster "data/coverage/11/1_pgrouting-alpha_60.tif"))

  (time (count-population raster1))
  (time (multiply-population! raster1 0.5))

  (time (count-population-under-coverage raster1 raster2))
  (time (multiply-population-under-coverage! raster1 raster2 0.9)))
