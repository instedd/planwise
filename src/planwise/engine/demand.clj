(ns planwise.engine.demand
  (:require [clojure.spec.alpha :as s]
            [planwise.engine.raster :as raster])
  (:import [planwise.engine Algorithm]
           [org.gdal.gdalconst gdalconst]))

(defn count-population
  [population]
  (let [{:keys [data nodata]} population]
    (Algorithm/countPopulation data nodata)))

(defn get-coverage
  "Retrieves pixel index under coverage"
  [population coverage]
  (let [{src-bounds :src dst-bounds :dst}       (raster/clipped-coordinates population coverage)
        {src-buffer :data src-nodata :nodata}   coverage
        {dst-buffer :data dst-nodata :nodata}   population
        [dst-left dst-top dst-right dst-bottom] dst-bounds
        [src-left src-top _ _]                  src-bounds
        dst-stride                              (:xsize population)
        src-stride                              (:xsize coverage)]
    (Algorithm/getPointsOfCoverage dst-buffer dst-stride dst-nodata
                                   src-buffer src-stride src-nodata
                                   dst-left dst-top dst-right dst-bottom
                                   src-left src-top)))

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

(defn mark-pixels-under-coverage!
  [raster mask mark-value]
  (let [{src-bounds :src dst-bounds :dst}       (raster/clipped-coordinates raster mask)
        {src-buffer :data src-nodata :nodata}   mask
        {dst-buffer :data dst-nodata :nodata}   raster
        [dst-left dst-top dst-right dst-bottom] dst-bounds
        [src-left src-top _ _]                  src-bounds
        dst-stride                              (:xsize raster)
        src-stride                              (:xsize mask)]
    (Algorithm/markPixelsUnderCoverage dst-buffer dst-stride dst-nodata
                                       src-buffer src-stride src-nodata
                                       mark-value
                                       dst-left dst-top dst-right dst-bottom
                                       src-left src-top)))
(defn get-coordinates
  [geotransform, x, y]
  (let [lon   (+ (get geotransform 0) (* (get geotransform 1) x) (* (get geotransform 2) y))
        lat   (+ (get geotransform 3) (* (get geotransform 4) x) (* (get geotransform 5) y))]

    {:lon lon :lat lat}))

(def earth-radius "In Km" 6371)

(defn- sin-2
  "Sin squared"
  [radians]
  (* (Math/sin radians) (Math/sin radians)))

(defn- trig
  [lat1 lat2 d-lat d-long]
  (+ (sin-2 (/ d-lat 2))
     (* (sin-2 (/ d-long 2)) (Math/cos lat1) (Math/cos lat2))))

(defn distance
  "Returns the distance in km between two points using haversine"
  [{lat1 :lat long1 :lon}
   {lat2 :lat long2 :lon}]
  (let [d-lat       (Math/toRadians (- lat2 lat1))
        d-long      (Math/toRadians (- long2 long1))
        lon1        (Math/toRadians long1)
        lon2        (Math/toRadians long2)
        lat1        (Math/toRadians lat1)
        lat2        (Math/toRadians lat2)]

    (* earth-radius 2
       (Math/asin (Math/sqrt (trig lat1 lat2 d-lat d-long))))))


(defn get-pixel-size-in-km2
  ; "return the average area a pixel covers, in squared meters"
  "Return the average number of pixels needed to cover a squared km"
  [raster]
  (let [[x1 _ _ y1]       (:geotransform raster)
        geotransform      (:geotransform raster)
        xsize             (:xsize raster)
        ysize             (:ysize raster)
        upper-left        (get-coordinates geotransform 0.0 0.0)
        upper-right       (get-coordinates geotransform xsize 0.0)
        lower-left        (get-coordinates geotransform 0.0 ysize)
        total-width-kms   (distance upper-left upper-right)
        pixel-width-kms   (/ total-width-kms xsize)
        total-height-kms  (distance upper-left lower-left)
        pixel-height-kms  (/ total-height-kms ysize)]

    (* pixel-height-kms pixel-width-kms)))

(defn build-mask!
  ; Threshold is to ignore areas with population per km2 smaller than the received value
  [raster mask mask-value threshold]
  (let [{src-buffer :data src-nodata :nodata} mask
        {dst-buffer :data}                    raster
        [left top right bottom]               [0 0 (dec (:xsize raster)) (dec (:ysize raster))]
        stride                                (:xsize raster)
        pixel-size                            (get-pixel-size-in-km2 raster)
        threshold-per-pixel                   (* threshold pixel-size)] ; Calculate the number each pixel needs to have to be bigger than the threshold

    (Algorithm/buildMask dst-buffer src-buffer src-nodata mask-value threshold-per-pixel stride left top right bottom)))


(defn compute-geo-coverage
  [{:keys [xsize ysize ^byte nodata ^bytes data]}]
  (loop [not-covered 0
         covered     0
         ^long index (dec (* xsize ysize))]
    (if (>= index 0)
      (let [value (aget data index)]
        (cond
          (= nodata value) (recur not-covered covered (dec index))
          (zero? value)    (recur (inc not-covered) covered (dec index))
          :else            (recur not-covered (inc covered) (dec index))))
      (float (/ covered (+ covered not-covered))))))

(defn compute-population-quartiles
  [population]
  (vec (Algorithm/computeExactQuartiles (:data population) (:nodata population))))

(defn build-renderable-population
  [population quartiles]
  (let [pixel-count (count (:data population))
        pixels      (byte-array pixel-count)
        ;; Java  bytes are signed, so this should be -1, but we write 255 for
        ;; consistency with Mapserver style definition
        nodata      (unchecked-byte 255)]
    (Algorithm/mapDataForRender (:data population) (:nodata population) pixels nodata (float-array quartiles))
    (raster/map->Raster (assoc population
                               :data pixels
                               :nodata nodata
                               :data-type gdalconst/GDT_Byte))))

(defn find-max-demand
  [demand]
  (let [index (Algorithm/findMaxIndex (:data demand) (:nodata demand))]
    (when (> index 0)
      (let [xsize                     (:xsize demand)
            x                         (mod index xsize)
            y                         (Math/floor (float (/ index xsize)))
            [xoff xres _ yoff _ yres] (:geotransform demand)
            lon                       (+ xoff (* xres x))
            lat                       (+ yoff (* yres y))
            value                     (aget (:data demand) index)]
        {:lon   lon
         :lat   lat
         :index index
         :value value}))))


(comment
  (def raster1 (raster/read-raster "data/populations/data/20/42.tif"))
  (def raster2 (raster/read-raster "data/coverage/11/1_pgrouting-alpha_60.tif"))

  (time (count-population raster1))
  (time (multiply-population! raster1 0.5))

  (time (count-population-under-coverage raster1 raster2))
  (time (multiply-population-under-coverage! raster1 raster2 0.9))

  (def quartiles (compute-population-quartiles raster1))
  (def renderable (build-renderable-population raster1 quartiles))

  (raster/write-raster renderable "data/test_scenario.tif"))
