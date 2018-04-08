(ns planwise.engine.demand
  (:require [clojure.spec.alpha :as s]
            [planwise.util.numbers :refer [float=]])
  (:import [org.gdal.gdal gdal]
           [org.gdal.gdalconst gdalconst]
           [org.gdal.osr SpatialReference]))

(gdal/AllRegister)

(defrecord Raster [projection geotransform xsize ysize data-type nodata data])

(defn- read-nodata-value
  "Reads the NODATA value from a raster band and returns it casted to the
  appropriate raster data type, or nil if the band doesn't have NODATA value"
  [band]
  (let [buffer    (make-array Double 1)
        data-type (.GetRasterDataType band)]
    (.GetNoDataValue band buffer)
    (when-let [value (aget buffer 0)]
      (condp = data-type
        gdalconst/GDT_Byte    (byte value)
        gdalconst/GDT_Float32 (float value)
        gdalconst/GDT_Float64 (double value)
        (throw (ex-info "Unsupported raster data type"
                        {:data-type data-type}))))))

(defn- read-band-data
  "Reads the data slice from the raster band and returns a Java array of a type
  appropriate to the band data type"
  [band xoff yoff xsize ysize]
  (let [data-type (.GetRasterDataType band)
        length    (* xsize ysize)
        buffer    (condp = data-type
                    gdalconst/GDT_Byte    (byte-array length)
                    gdalconst/GDT_Float32 (float-array length)
                    gdalconst/GDT_Float64 (double-array length)
                    (throw (ex-info "Unsupported raster data type"
                                    {:data-type data-type})))
        err       (.ReadRaster band xoff yoff xsize ysize data-type buffer)]
    (if (= gdalconst/CE_None err)
      buffer
      (throw (ex-info "Error reading raster data"
                      {:offset    [xoff yoff]
                       :size      [xsize ysize]
                       :data-type data-type})))))

(defn read-raster
  "Reads a raster file and returns a Raster record with the data from the
  specified band number (defaults to 1)"
  ([path]
   (read-raster path 1))
  ([path band-number]
   (if-let [dataset (gdal/Open path gdalconst/GA_ReadOnly)]
     (try
       (let [projection   (.GetProjection dataset)
             geotransform (.GetGeoTransform dataset)
             raster-count (.GetRasterCount dataset)]
         (if (<= 1 band-number raster-count)
           (let [band      (.GetRasterBand dataset band-number)
                 data-type (.GetRasterDataType band)
                 xsize     (.GetXSize band)
                 ysize     (.GetYSize band)
                 nodata    (read-nodata-value band)
                 data      (read-band-data band 0 0 xsize ysize)]
             (->Raster projection geotransform xsize ysize data-type nodata data))
           (throw (ex-info "Invalid band number"
                           {:filename   path
                            :band       band-number
                            :band-count raster-count}))))
       (finally (.delete dataset)))
     (throw (ex-info "Failed to open raster file"
                     {:filename path})))))

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

(defprotocol RasterOps
  (compatible? [r1 r2]
    "Returns true if both rasters are compatible, as in having equivalent
    projections and 'equal' pixel resolutions")
  (aligned? [r1 r2]
    "Returns true if both rasters are grid-aligned")
  (clipped-coordinates [r1 r2]
    "Returns the grid bounds of r2 in r1 buffer coordinate space"))

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
      (grids-aligned? geoxform1 geoxform2))))

(comment
  (def raster1 (read-raster "data/populations/data/17/42.tif"))
  (def raster2 (read-raster "data/coverage/11/1_pgrouting-alpha_60.tif"))

  (compatible? raster1 raster2) ;; => true
  (aligned? raster1 raster2)    ;; => false

  (grid-offset (:geotransform raster1) (:geotransform raster2))
  (grid-clipped-coordinates raster1 raster2)
)
