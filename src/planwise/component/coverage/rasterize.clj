(ns planwise.component.coverage.rasterize
  (:require [clojure.spec.alpha :as s]
            [planwise.boundary.coverage :as boundary]
            [planwise.util.geo :as geo])
  (:import [org.gdal.gdal gdal]
           [org.gdal.ogr ogr]
           [org.gdal.ogr ogr Feature FeatureDefn Geometry]
           [org.gdal.gdalconst gdalconst]
           [org.gdal.osr osrConstants SpatialReference]
           [org.postgis PGgeometry]))

(s/def ::resolution ::boundary/raster-resolution)
(s/def ::ref-coords ::geo/coords)
(s/def ::rasterize-options (s/keys :req-un [::ref-coords ::resolution]))

(gdal/AllRegister)
(ogr/RegisterAll)

(defn memory-datasource
  ([]
   (memory-datasource "memory-ds"))
  ([name]
   (let [driver (ogr/GetDriverByName "Memory")]
     (.CreateDataSource driver name))))

(defn build-datasource-from-geometry
  [srs geometry]
  (let [mem-ds (memory-datasource)
        layer (.CreateLayer mem-ds "geometry" srs)
        feature-defn (.GetLayerDefn layer)
        feature (doto (Feature. feature-defn)
                  (.SetGeometryDirectly geometry))]
    (.CreateFeature layer feature)
    mem-ds))

(defn build-mask-raster-file
  [name {:keys [srs width height geotransform]}]
  (let [block-size-x 128
        block-size-y 128
        driver (gdal/GetDriverByName "GTiff")
        options (into-array String ["NBITS=1"
                                    "COMPRESS=CCITTFAX4"
                                    "TILED=YES"
                                    (str "BLOCKXSIZE=" block-size-x)
                                    (str "BLOCKYSIZE=" block-size-y)])
        dataset (.Create driver name width height 1 gdalconst/GDT_Byte options)]
    (doto dataset
      (.SetProjection (.ExportToWkt srs))
      (.SetGeoTransform (double-array geotransform)))
    (.. dataset (GetRasterBand 1) (SetNoDataValue 0.0))
    dataset))

(defn build-temporary-raster-mask
  [{:keys [srs width height geotransform]}]
  (let [block-size-x 128
        block-size-y 128
        driver (gdal/GetDriverByName "MEM")
        dataset (.Create driver "" width height 1 gdalconst/GDT_Byte)]
    (doto dataset
      (.SetProjection (.ExportToWkt srs))
      (.SetGeoTransform (double-array geotransform)))
    (.. dataset (GetRasterBand 1) (SetNoDataValue 0.0))
    dataset))

(defn compute-aligned-raster-extent
  [{:keys [min-lon min-lat max-lon max-lat] :as envelope}
   {ref-lat :lat ref-lon :lon}
   {:keys [xres yres]}]
  (let [off-lon    (- min-lon ref-lon)
        off-lat    (- max-lat ref-lat)
        off-width  (Math/floor (/ off-lon xres))
        off-height (Math/ceil (/ off-lat yres))
        ul-lon     (+ ref-lon (* xres off-width))
        ul-lat     (+ ref-lat (* yres off-height))
        env-width  (- max-lon ul-lon)
        env-height (- max-lat ul-lat)
        width      (inc (int (Math/ceil (/ env-width (Math/abs xres)))))
        height     (inc (int (Math/ceil (/ env-height (Math/abs yres)))))]
    {:width        width
     :height       height
     :geotransform [ul-lon xres    0
                    ul-lat    0 yres]}))

(defn rasterize
  ([polygon output-path {:keys [ref-coords resolution] :as options}]
   {:pre [(s/valid? ::geo/pg-polygon polygon)
          (s/valid? string? output-path)
          (s/valid? ::rasterize-options options)]}
   (let [srs            (geo/pg->srs polygon)
         geometry       (geo/pg->geometry polygon)
         envelope       (geo/geometry->envelope geometry)
         aligned-extent (compute-aligned-raster-extent envelope ref-coords resolution)
         datasource     (build-datasource-from-geometry srs geometry)
         layer          (.GetLayer datasource 0)
         raster         (build-mask-raster-file output-path (assoc aligned-extent :srs srs))]
     (gdal/RasterizeLayer raster
                          (int-array [1])
                          layer
                          (double-array [255.0]))
     (.FlushCache raster)

    ;; Cleanup GDAL objects
     (.delete raster)
     (.delete datasource)
     (.delete srs)))

  ([polygon]
   {:pre [(s/valid? ::geo/pg-polygon polygon)]}
   (let [ref-coords     {:lat 0 :lon 0}
         resolution     {:xres 1/1200 :yres -1/1200}
         srs            (geo/pg->srs polygon)
         geometry       (geo/pg->geometry polygon)
         envelope       (geo/geometry->envelope geometry)
         aligned-extent (compute-aligned-raster-extent envelope ref-coords resolution)
         datasource     (build-datasource-from-geometry srs geometry)
         layer          (.GetLayer datasource 0)
         raster         (build-temporary-raster-mask (assoc aligned-extent :srs srs))]
     (gdal/RasterizeLayer raster
                          (int-array [1])
                          layer
                          (double-array [255.0])) raster)))

(comment
  (require '[planwise.boundary.coverage :as coverage])
  (def pg (coverage/compute-coverage (:planwise.component/coverage integrant.repl.state/system)
                                     {:lat -3.0361 :lon 40.1333}
                                     {:algorithm :driving-friction
                                      :driving-time 60}))

  ;; (def ref-coords {:lat 5.470694601152364 :lon 33.912608425216725})
  (def ref-coords {:lon 39.727577500000002 :lat -2.631561400000000})
  ;; (def pixel-resolution {:xres 8.333000000000001E-4 :yres -8.333000000000001E-4})
  (def pixel-resolution {:xres 1/1200 :yres -1/1200})

  (rasterize pg "test.tif" {:ref-coords ref-coords :resolution pixel-resolution}))
