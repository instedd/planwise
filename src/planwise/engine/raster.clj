(ns planwise.engine.raster
  (:require [clojure.spec.alpha :as s]
            [planwise.util.geo :as geo]
            [planwise.util.numbers :refer [float= abs]])
  (:import [org.gdal.gdal gdal]
           [org.gdal.gdalconst gdalconst]
           [org.gdal.osr SpatialReference]))

;;
;; WARNING: all operations in this file assume raster files that have no rotation/tilting
;;

(gdal/AllRegister)

(defrecord Raster [projection geotransform xsize ysize data-type nodata data])

(defn- cast-value
  [data-type value]
  (condp = data-type
    gdalconst/GDT_Byte    (byte value)
    gdalconst/GDT_Int16   (short value)
    gdalconst/GDT_Int32   (int value)
    gdalconst/GDT_Float32 (float value)
    gdalconst/GDT_Float64 (double value)
    (throw (ex-info "Unsupported data type"
                    {:data-type data-type}))))

(defn- alloc-array
  ([data-type length]
   (alloc-array data-type length 0))
  ([data-type length val]
   (let [initial-value (cast-value data-type val)]
     (condp = data-type
       gdalconst/GDT_Byte    (byte-array length initial-value)
       gdalconst/GDT_Int16   (short-array length initial-value)
       gdalconst/GDT_Int32   (int-array length initial-value)
       gdalconst/GDT_Float32 (float-array length initial-value)
       gdalconst/GDT_Float64 (double-array length initial-value)
       (throw (ex-info "Unsupported data type"
                       {:data-type data-type}))))))

(defn read-nodata-value
  "Reads the NODATA value from a raster band and returns it casted to the
  appropriate raster data type, or nil if the band doesn't have NODATA value"
  [band]
  (let [buffer    (make-array Double 1)
        data-type (.GetRasterDataType band)]
    (.GetNoDataValue band buffer)
    (when-let [value (aget buffer 0)]
      (cast-value data-type value))))

(defn read-band-data
  "Reads the data slice from the raster band and returns a Java array of a type
  appropriate to the band data type"
  [band xoff yoff xsize ysize]
  (let [data-type (.GetRasterDataType band)
        length    (* xsize ysize)
        buffer    (alloc-array data-type length)
        err       (.ReadRaster band xoff yoff xsize ysize data-type buffer)]
    (if (= gdalconst/CE_None err)
      buffer
      (throw (ex-info "Error reading raster data"
                      {:offset    [xoff yoff]
                       :size      [xsize ysize]
                       :data-type data-type})))))

(defn with-open-raster
  [path f]
  (if-let [dataset (gdal/Open path gdalconst/GA_ReadOnly)]
    (try
      (f dataset)
      (finally (.delete dataset)))
    (throw (ex-info "Failed to open raster file"
                    {:filename path}))))

(defn create-raster
  [dataset]
  (let [projection   (.GetProjection dataset)
        geotransform (.GetGeoTransform dataset)
        raster-count (.GetRasterCount dataset)
        band      (.GetRasterBand dataset 1)
        data-type (.GetRasterDataType band)
        xsize     (.GetXSize band)
        ysize     (.GetYSize band)
        nodata    (read-nodata-value band)
        data      (read-band-data band 0 0 xsize ysize)]
    (->Raster projection geotransform xsize ysize data-type nodata data)))

(defn create-raster-from
  [source-raster {:keys [data-type nodata data-fill]}]
  (let [{:keys [projection geotransform xsize ysize]} source-raster
        data-type                                     (or data-type (:data-type source-raster))
        nodata                                        (or nodata (:nodata source-raster))
        data                                          (alloc-array data-type (* xsize ysize) data-fill)]
    (->Raster projection geotransform xsize ysize data-type nodata data)))

(defn read-raster
  "Reads a raster file and returns a Raster record with the data from the
  specified band number (defaults to 1)"
  ([path]
   (read-raster path 1))
  ([path band-number]
   (with-open-raster path
     (fn [dataset]
       (let [projection   (.GetProjection dataset)
             geotransform (.GetGeoTransform dataset)
             raster-count (.GetRasterCount dataset)]
         (cond
           (nil? band-number)
           (let [xsize (.GetRasterXSize dataset)
                 ysize (.GetRasterYSize dataset)]
             (-> (->Raster projection geotransform xsize ysize nil nil nil)
                 (assoc :file-path path)))

           (<= 1 band-number raster-count)
           (let [band      (.GetRasterBand dataset band-number)
                 data-type (.GetRasterDataType band)
                 xsize     (.GetXSize band)
                 ysize     (.GetYSize band)
                 nodata    (read-nodata-value band)
                 data      (read-band-data band 0 0 xsize ysize)]
             (-> (->Raster projection geotransform xsize ysize data-type nodata data)
                 (assoc :file-path path)))

           :else
           (throw (ex-info "Invalid band number"
                           {:filename   path
                            :band       band-number
                            :band-count raster-count}))))))))

(defn read-raster-without-data
  [path]
  (read-raster path nil))

(defn- compute-band-histogram
  [band num-buckets]
  (let [data-type (.GetRasterDataType band)
        min-max   (double-array 2)
        _         (.ComputeRasterMinMax band min-max)
        buckets   (int-array num-buckets)
        min-value (aget min-max 0)
        max-value (aget min-max 1)
        err       (.GetHistogram band min-value max-value buckets true false)]
    (if (= gdalconst/CE_None err)
      {:min      (cast-value data-type min-value)
       :max      (cast-value data-type max-value)
       :buckets  buckets})))

(defn compute-raster-histogram
  ([path]
   (compute-raster-histogram path 1))
  ([path band-number]
   (with-open-raster path
     (fn [dataset]
       (let [raster-count (.GetRasterCount dataset)]
         (if (<= 1 band-number raster-count)
           (let [band (.GetRasterBand dataset band-number)]
             (compute-band-histogram band 256))
           (throw (ex-info "Invalid band number"
                           {:filename   path
                            :band       band-number
                            :band-count raster-count}))))))))

(defn- raster-options
  [data-type]
  (let [block-xsize   256
        block-ysize   256
        base-options  ["TILED=YES"
                       (str "BLOCKXSIZE=" block-xsize)
                       (str "BLOCKYSIZE=" block-ysize)]
        extra-options (condp = data-type
                        gdalconst/GDT_Byte    ["COMPRESS=LZW"
                                               "PREDICTOR=2"]
                        gdalconst/GDT_Float32 ["COMPRESS=LZW"
                                               "PREDICTOR=3"])]
    (concat base-options extra-options)))

(defn write-raster-file
  [{:keys [xsize ysize data-type nodata data projection geotransform] :as raster} output-path]
  (when (nil? data)
    (throw (ex-info "Cannot write raster with nil data" {:raster raster})))
  (let [driver      (gdal/GetDriverByName "GTiff")
        options     (into-array String (raster-options data-type))
        dataset     (.Create driver output-path xsize ysize 1 data-type options)]
    (doto dataset
      (.SetProjection projection)
      (.SetGeoTransform geotransform))
    (doto (.GetRasterBand dataset 1)
      (.SetNoDataValue nodata)
      (.WriteRaster 0 0 xsize ysize data))
    (doto dataset
      (.FlushCache)
      (.delete))
    nil))

(defn- equal-resolution?
  [geoxform1 geoxform2]
  (let [[_ xres1 _ _ _ yres1] geoxform1
        [_ xres2 _ _ _ yres2] geoxform2]
    (and (float= xres1 xres2 0.001)
         (float= yres1 yres2 0.001))))

(defn- grids-aligned?
  [geoxform1 geoxform2]
  (let [[xoff1 xres _ yoff1 _ yres] geoxform1
        [xoff2    _ _ yoff2 _    _] geoxform2
        xdelta                      (- xoff2 xoff1)
        ydelta                      (- yoff2 yoff1)
        xpixels                     (/ xdelta xres)
        ypixels                     (/ ydelta yres)]
    (and (equal-resolution? geoxform1 geoxform2)
         (float= xpixels (Math/round xpixels))
         (float= ypixels (Math/round ypixels)))))

(defn- grid-offset
  "Computes the grid offset coordinates for geoxform2 with geoxform1 as
  reference and origin"
  [geoxform1 geoxform2]
  (let [[xoff1 xres _ yoff1 _ yres] geoxform1
        [xoff2    _ _ yoff2 _    _] geoxform2
        offset-x                    (Math/round (/ (- xoff2 xoff1) xres))
        offset-y                    (Math/round (/ (- yoff2 yoff1) yres))]
    [offset-x offset-y]))

(defn grid-clipped-coordinates
  "Computes the bounds src in dst and returns the result in pixel coordinates
  (left, top, right, bottom), assuming compatible rasters; returns nil if there
  is no overlap between the two rasters"
  [dst src]
  (let [[dst-width dst-height] ((juxt :xsize :ysize) dst)
        [src-width src-height] ((juxt :xsize :ysize) src)
        [dst-left dst-top]     (grid-offset (:geotransform dst) (:geotransform src))
        [src-left src-top]     [0 0]
        [dst-right dst-bottom] [(+ dst-left src-width) (+ dst-top src-height)]
        [src-right src-bottom] [src-width src-height]
        clip-left              (if (< 0 dst-left) 0 (- dst-left))
        clip-right             (if (< dst-right dst-width) 0 (- dst-right dst-width))
        clip-top               (if (< 0 dst-top) 0 (- dst-top))
        clip-bottom            (if (< dst-bottom dst-height) 0 (- dst-bottom dst-height))
        src-left               (+ src-left clip-left)
        src-right              (- src-right clip-right)
        src-top                (+ src-top clip-top)
        src-bottom             (- src-bottom clip-bottom)
        dst-left               (+ dst-left clip-left)
        dst-right              (- dst-right clip-right)
        dst-top                (+ dst-top clip-top)
        dst-bottom             (- dst-bottom clip-bottom)]
    (when (and (< dst-left dst-right)
               (< dst-top dst-bottom))
      {:dst [dst-left dst-top (dec dst-right) (dec dst-bottom)]
       :src [src-left src-top (dec src-right) (dec src-bottom)]})))

(defn raster-envelope
  "Computes the envelope of the raster file assuming no rotation in the
  geotransform matrix; applies buffer as measured in pixels"
  [{:keys [geotransform xsize ysize]} buffer-pixels]
  (let [[x1 xres _ y1 _ yres] geotransform
        x2                    (+ x1 (* xres xsize))
        y2                    (+ y1 (* yres ysize))]

    {:min-lat (- (min y1 y2) (* buffer-pixels (abs yres)))
     :max-lat (+ (max y1 y2) (* buffer-pixels (abs yres)))
     :min-lon (- (min x1 x2) (* buffer-pixels (abs xres)))
     :max-lon (+ (max x1 x2) (* buffer-pixels (abs xres)))}))

(defn raster-resolution
  [{:keys [geotransform]}]
  (let [[_ xres _ _ _ yres] geotransform]
    {:xres xres :yres yres}))

(defn pixel-coords->lat-lon
  [{:keys [geotransform]}, x, y]
  (let [lon   (+ (get geotransform 0) (* (get geotransform 1) x) (* (get geotransform 2) y))
        lat   (+ (get geotransform 3) (* (get geotransform 4) x) (* (get geotransform 5) y))]

    {:lat lat :lon lon}))

(defn get-pixel-size-in-km2
  ; "return the average area a pixel covers, in squared meters"
  "Return the average number of pixels needed to cover a squared km"
  [raster]
  (let [xsize             (:xsize raster)
        ysize             (:ysize raster)
        upper-left        (pixel-coords->lat-lon raster 0.0 0.0)
        upper-right       (pixel-coords->lat-lon raster xsize 0.0)
        lower-left        (pixel-coords->lat-lon raster 0.0 ysize)
        total-width-kms   (geo/haversine-distance upper-left upper-right)
        pixel-width-kms   (/ total-width-kms xsize)
        total-height-kms  (geo/haversine-distance upper-left lower-left)
        pixel-height-kms  (/ total-height-kms ysize)]

    (* pixel-height-kms pixel-width-kms)))

(defprotocol RasterOps
  (compatible? [r1 r2]
    "Returns true if both rasters are compatible, as in having equivalent
    projections and 'equal' pixel resolutions")
  (aligned? [r1 r2]
    "Returns true if both rasters are grid-aligned")
  (clipped-coordinates [r1 r2]
    "Returns the grid bounds of r2 in r1 buffer coordinate space")
  (write-raster [r path]
    "Writes the raster into a TIF file")
  (envelope [r]
    "Returns a map with the bounding box coordinates of latitude and longitude :min-lat :max-lat :min-lon :max-lon"))

(extend-type Raster
  RasterOps
  (compatible? [r1 r2]
    (let [srs1 (SpatialReference.)
          srs2 (SpatialReference.)
          geoxform1 (:geotransform r1)
          geoxform2 (:geotransform r2)]
      (try
        (.ImportFromWkt srs1 (:projection r1))
        (.ImportFromWkt srs2 (:projection r2))
        (let [same-srs?  (not (zero? (.IsSame srs1 srs2)))
              equal-res? (equal-resolution? geoxform1 geoxform2)]
          (and same-srs? equal-res?))
        (finally
          (.delete srs1)
          (.delete srs2)))))

  (aligned? [r1 r2]
    (let [geoxform1 (:geotransform r1)
          geoxform2 (:geotransform r2)]
      (grids-aligned? geoxform1 geoxform2)))

  (clipped-coordinates [r1 r2]
    (grid-clipped-coordinates r1 r2))

  (write-raster [r path]
    (write-raster-file r path))

  (envelope [r]
    (raster-envelope r 0)))

(comment
  (def raster1 (time (read-raster "data/populations/data/20/42.tif")))
  (def raster2 (read-raster "data/coverage/11/1_pgrouting-alpha_60.tif"))

  (def hist1 (time (compute-raster-histogram "data/populations/data/20/1.tif")))

  (compatible? raster1 raster2) ;; => true
  (aligned? raster1 raster2)    ;; => false

  (clipped-coordinates raster1 raster2)

  (write-raster raster1 "/tmp/output.tif"))
