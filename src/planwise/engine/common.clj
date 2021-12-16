(ns planwise.engine.common
  (:require [planwise.boundary.providers-set :as providers-set]
            [planwise.boundary.runner :as runner]
            [planwise.engine.raster :as raster]
            [planwise.util.numbers :refer [abs float=]]
            [clojure.java.io :as io]
            [clojure.set :as set]
            [clojure.string :as str]
            [taoensso.timbre :as timbre]))

(def ^:dynamic *script-timeout-ms* 30000)
(def ^:dynamic *bin-timeout-ms* 20000)

(timbre/refer-timbre)

;; Project configuration ======================================================
;;

(defn is-project-raster?
  [project]
  (let [type (:source-type project)]
    (when (nil? type) (throw (ex-info "Missing source set/project type" {:project project})))
    (= "raster" type)))

(defn coverage-context
  "Returns the coverage context that should be used for this project"
  [project]
  [:project (:id project)])

(defn coverage-criteria-for-project
  "Criteria options from project for usage in coverage/compute-coverage-polygon."
  [project]
  (let [coverage-algorithm (keyword (:coverage-algorithm project))
        project-config     (:config project)
        coverage-options   (get-in project-config [:coverage :filter-options])]
    (assoc coverage-options :algorithm coverage-algorithm)))

(defn filter-options-for-project
  "Filter options from project for usage in providers-set/get-providers-in-region."
  [project]
  (let [region-id (:region-id project)
        tags      (get-in project [:config :providers :tags])]
    {:region-id region-id
     :tags      tags}))


;; Path helpers ===============================================================
;; TODO: migrate these to file-store

(defn scenario-raster-full-path
  "Given a raster path from scenario-raster-path, return the full path to the raster file."
  [raster-name]
  (str "data/" raster-name ".tif"))


;; Provider selection =========================================================
;;

(defn- provider-mapper
  "Returns a mapper function for providers into the shape required for computing scenarios."
  [provider-set-id applicable]
  (fn [provider]
    (-> provider
        (select-keys [:id :lat :lon :name :capacity])
        (assoc :applicable? applicable))))

(defn providers-in-project
  "Fetches all providers in the project's region and builds a collection with an
  extra attribute indicating if they apply (wrt. tag filtering). Returned
  providers have :id, :name, :capacity, :applicable."
  [providers-service project]
  (let [provider-set-id (:provider-set-id project)
        provider-set    (providers-set/get-provider-set providers-service provider-set-id)
        version         (or (:provider-set-version project)
                            (:last-version provider-set))
        filter-options  (filter-options-for-project project)
        all-providers   (providers-set/get-providers-in-region providers-service
                                                               provider-set-id
                                                               version
                                                               filter-options)
        applicable      (map (provider-mapper provider-set-id true) (:providers all-providers))
        non-applicable  (map (provider-mapper provider-set-id false) (:disabled-providers all-providers))
        providers       (concat applicable non-applicable)]
    (->> providers
         (sort-by :capacity)
         reverse)))


;; Raster related functions ===================================================
;;

(defn estimate-envelope-raster-size
  "Compute the approximate size (in pixels) for an area given an envelope and a
  raster to get the resolution."
  [raster envelope]
  (let [{:keys [xres yres]}                       (raster/raster-resolution raster)
        {:keys [min-lon max-lon min-lat max-lat]} envelope]
    {:xsize (Math/ceil (/ (- max-lon min-lon) (abs xres)))
     :ysize (Math/ceil (/ (- max-lat min-lat) (abs yres)))}))

(defn compute-down-scaling-factor
  "Compute an integer down-scaling factor to reduce a raster size to a maximum
  number of pixels. Scale should be applied uniformily to width and height."
  [{:keys [xsize ysize]} max-pixels]
  (Math/ceil (Math/sqrt (/ (* xsize ysize) max-pixels))))

(defn resize-raster
  "Resize a raster by a scale factor using external tool gdalwarp. Returns the
  original raster if scale factor is 1."
  [runner raster output-path scale-factor]
  (if (float= 1.0 scale-factor)
    raster
    (let [input-path          (:file-path raster)
          {:keys [xres yres]} (raster/raster-resolution raster)
          args                (map str ["-i" input-path
                                        "-o" output-path
                                        "-r" (* scale-factor xres) (* scale-factor yres)])]
      (io/make-parents output-path)
      (io/delete-file output-path :silent)
      (runner/run-external runner :scripts *script-timeout-ms* "resize-raster" args)
      (raster/read-raster-without-data output-path))))

(defn crop-raster-by-cutline
  "Crop a raster by a given GeoJSON contour using external tool gdalwarp."
  [runner raster geojson work-dir]
  (let [cutline-path        (str work-dir "/cutline.geojson")
        input-path          (:file-path raster)
        output-path         (str work-dir "/source-cropped.tif")
        {:keys [xres yres]} (raster/raster-resolution raster)
        args                (map str ["-i" input-path
                                      "-o" output-path
                                      "-c" cutline-path
                                      "-r" xres yres])]
    (io/make-parents cutline-path)
    (spit cutline-path geojson)
    (io/delete-file output-path :silent)
    (runner/run-external runner :scripts *script-timeout-ms* "crop-source-raster" args)
    (raster/read-raster-without-data output-path)))

(defn count-raster-demand
  "Using the external binary aggregate-population, compute the sum of all values
  of the given raster."
  [runner raster]
  (let [input-path (:file-path raster)
        args       [input-path]
        output     (runner/run-external runner :bin *bin-timeout-ms* "aggregate-population" args)]
    (-> output
        (str/split #"\s+")
        first
        Long/parseLong)))

(defn compute-resize-factor
  "Given two rasters (presumably the second being a down-scaled version of the
  first), compute the scaling factor to apply to each pixel such that the
  aggregate of the values of the pixels are equal.
  Since we are using PPP (population per pixel) rasters, we need this to account
  for the down-scaling done for optimization."
  [runner original-raster resized-raster]
  (if (= original-raster resized-raster)
    1.0
    (let [original-demand (count-raster-demand runner original-raster)
          resized-demand (count-raster-demand runner resized-raster)]
      (double (/ original-demand resized-demand)))))
