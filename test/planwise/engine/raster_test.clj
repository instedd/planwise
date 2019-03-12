(ns planwise.engine.raster-test
  (:require [planwise.engine.raster :as sut]
            [planwise.util.numbers :refer [float=]]
            [clojure.test :refer :all]))

(def sample-float-raster-path "test/resources/pointe-noire-float.tif")
(def sample-byte-raster-path "test/resources/pointe-noire-byte.tif")

(def identity-xf (double-array [0 1 0 0 0 1]))

(defn offset-xf
  [x y]
  (double-array [x 1 0 y 0 1]))

(def base-raster {:geotransform identity-xf :xsize 100 :ysize 100})

(deftest grid-clipped-coordinates-test
  (is (= {:dst [0 0 99 99] :src [0 0 99 99]}
         (sut/grid-clipped-coordinates base-raster
                                       {:geotransform identity-xf :xsize 100 :ysize 100}))
      "dst and src are equal")
  (is (= {:dst [25 25 74 74] :src [0 0 49 49]}
         (sut/grid-clipped-coordinates base-raster
                                       {:geotransform (offset-xf 25 25) :xsize 50 :ysize 50}))
      "src is fully contained in dst")
  (is (= {:dst [0 0 99 99] :src [25 25 124 124]}
         (sut/grid-clipped-coordinates base-raster
                                       {:geotransform (offset-xf -25 -25) :xsize 150 :ysize 150}))
      "clips src in all directions")
  (is (= {:dst [0 0 49 49] :src [25 25 74 74]}
         (sut/grid-clipped-coordinates base-raster
                                       {:geotransform (offset-xf -25 -25) :xsize 75 :ysize 75}))
      "clips src at the top and left")
  (is (= {:dst [50 50 99 99] :src [0 0 49 49]}
         (sut/grid-clipped-coordinates base-raster
                                       {:geotransform (offset-xf 50 50) :xsize 100 :ysize 100}))
      "clips src at bottom and right")
  (is (= {:dst [25 25 99 74] :src [0 0 74 49]}
         (sut/grid-clipped-coordinates
          {:geotransform (double-array [10.0 0.1 0 -5.0 0 -0.1]) :xsize 100 :ysize 100}
          {:geotransform (double-array [12.5 0.1 0 -7.5 0 -0.1]) :xsize 100 :ysize 50}))
      "works for arbitrary transformations"))

(deftest read-raster-without-data-test
  (let [raster (sut/read-raster-without-data sample-float-raster-path)]
    (is (some? raster))
    (is (= [107 102] [(:xsize raster) (:ysize raster)]))))

(deftest envelope-test
  (let [raster   (sut/read-raster-without-data sample-float-raster-path)
        envelope (sut/envelope raster)]
    (is (float= (:min-lat envelope) -4.8314734 10e-6))
    (is (float= (:max-lat envelope) -4.7464768 10e-6))
    (is (float= (:min-lon envelope) 11.8228604 10e-6))
    (is (float= (:max-lon envelope) 11.9120235 10e-6))))

(deftest raster-envelope-with-buffer
  (let [raster     (sut/read-raster-without-data sample-float-raster-path)
        env        (sut/raster-envelope raster 0)
        env-buffer (sut/raster-envelope raster 2)]
    (is (< (:min-lat env-buffer) (:min-lat env)))
    (is (> (:max-lat env-buffer) (:max-lat env)))
    (is (< (:min-lon env-buffer) (:min-lon env)))
    (is (> (:max-lon env-buffer) (:max-lon env)))))
