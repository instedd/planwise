(ns planwise.component.coverage.rasterize
  (:import [org.gdal.gdal gdal]
           [org.gdal.ogr ogr Feature FeatureDefn Geometry]
           [org.gdal.gdalconst gdalconst]
           [org.gdal.osr osrConstants SpatialReference]
           [org.postgis PGgeometry]))

(gdal/AllRegister)
(ogr/RegisterAll)

(defn memory-datasource
  ([]
   (memory-datasource "memory-ds"))
  ([name]
   (let [driver (ogr/GetDriverByName "Memory")]
     (.CreateDataSource driver name))))

(defn srs-from-epsg
  [epsg]
  (doto (SpatialReference.)
    (.ImportFromEPSG epsg)))

(defn srs-from-pg
  [pg]
  (let [srid (.. pg (getGeometry) (getSrid))]
    (srs-from-epsg srid)))

(defn pg->geometry
  [pg]
  (let [wkt (second (PGgeometry/splitSRID (str pg)))]
    (Geometry/CreateFromWkt wkt)))

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
                                    (str "BLOCKXSIZE=" block-size-x)
                                    (str "BLOCKYSIZE=" block-size-y)])
        dataset (.Create driver name width height 1 gdalconst/GDT_Byte options)]
    (doto dataset
      (.SetProjection (.ExportToWkt srs))
      (.SetGeoTransform (double-array geotransform)))
    (.. dataset (GetRasterBand 1) (SetNoDataValue 0.0))
    dataset))

(defn envelope
  [geom]
  (let [env (double-array 4)]
    (.GetEnvelope geom env)
    (let [[min-lng max-lng min-lat max-lat] env]
      {:min-lng min-lng
       :min-lat min-lat
       :max-lng max-lng
       :max-lat max-lat})))

(defn compute-aligned-raster-extent
  [{:keys [min-lng min-lat max-lng max-lat] :as envelope}
   {ref-lat :lat ref-lng :lng}
   {:keys [x-res y-res]}]
  (let [off-lng    (- min-lng ref-lng)
        off-lat    (- max-lat ref-lat)
        off-width  (Math/floor (/ off-lng x-res))
        off-height (Math/ceil (/ off-lat y-res))
        ul-lng     (+ ref-lng (* x-res off-width))
        ul-lat     (+ ref-lat (* y-res off-height))
        env-width  (- max-lng ul-lng)
        env-height (- ul-lat min-lat)
        width      (inc (int (Math/ceil (/ env-width x-res))))
        height     (inc (int (Math/ceil (/ env-height y-res))))]
    {:width        width
     :height       height
     :geotransform [ul-lng x-res         0
                    ul-lat     0 (- y-res)]}))

(defn rasterize
  [polygon output-path {:keys [ref-coords resolution] :as options}]
  (let [srs            (srs-from-pg polygon)
        geometry       (pg->geometry polygon)
        envelope       (envelope geometry)
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

(comment
  (require '[planwise.boundary.coverage :as coverage])
  (def pg (coverage/compute-coverage (:planwise.component/coverage integrant.repl.state/system)
                                     {:lat -3.0361 :lon 40.1333}
                                     {:algorithm :pgrouting-alpha
                                      :driving-time 45}))

  ;; (def ref-coords {:lat 5.470694601152364 :lng 33.912608425216725})
  (def ref-coords {:lng 39.727577500000002 :lat -2.631561400000000})
  ;; (def pixel-resolution {:x-res 8.333000000000001E-4 :y-res 8.333000000000001E-4})
  (def pixel-resolution {:x-res 1/1200 :y-res 1/1200})

  (rasterize pg "test.tif" {:ref-coords ref-coords :resolution pixel-resolution}))
