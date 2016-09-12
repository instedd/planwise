(ns planwise.component.maps
  (:require [com.stuartsierra.component :as component]
            [planwise.component.runner :refer [run-external]]
            [planwise.boundary.regions :as regions]
            [clojure.string :as str]
            [planwise.util.str :refer [trim-to-int]]
            [digest :as digest]
            [taoensso.timbre :as timbre]))

(timbre/refer-timbre)

(defn mapserver-url
  [{config :config}]
  (:mapserver-url config))

(defn calculate-demand?
  [{config :config}]
  (boolean (:calculate-demand config)))

(defn- demand-map-key
  [region-id polygons-with-capacities]
  (digest/sha-256
    (str/join "_" (cons region-id polygons-with-capacities))))

(defn- default-capacity
  [{config :config}]
  (:facilities-capacity config))

(defn- data-path
  [{config :config} & args]
  (apply str (:data config) args))

(defn- demands-path
  [service & args]
  (apply data-path service (cons "demands/" args)))

(defn- populations-path
  [service & args]
  (apply data-path service (cons "populations/data/" args)))

(defn- isochrones-path
  [service & args]
  (apply data-path service (cons "isochrones/" args)))

(defn- capacity-for
  [service {:keys [population population-in-region]}]
  (let [capacity (default-capacity service)
        factor   (if population-in-region (/ population-in-region population) 1)]
    (int (* factor capacity))))

(defn- create-map-raster
  [service map-key region-id]
  (let [{max-population :max-population} (regions/find-region (:regions service) region-id)]
    (run-external
        (:runner service)
        nil
        30000
        "gdal_translate"
        "-q" "-ot" "Byte" "-scale" "0" (str max-population) "0" "255"
        (demands-path service map-key ".data.tif")
        (demands-path service map-key ".tif"))))

(defn demand-map
  [service region-id facilities]
  (when (calculate-demand? service)
    (try
      (let [polygons (filter :polygon-id facilities)
            polygons-with-capacities (->> polygons
                                        (map (juxt
                                              #(isochrones-path service region-id "/" (:polygon-id %) ".tif")
                                              #(str (capacity-for service %))))
                                        (flatten))
            map-key  (demand-map-key region-id polygons-with-capacities)
            response (apply run-external
                        (:runner service)
                        :bin
                        180000
                        "calculate-demand"
                        (demands-path service map-key ".data.tif")
                        (populations-path service region-id ".tif")
                        (vec polygons-with-capacities))
            unsatisfied-count (trim-to-int response)
            _ (create-map-raster service map-key region-id)]
          {:map-key map-key,
           :unsatisfied-count unsatisfied-count})
      (catch Exception e
        (error e "Error calculating demand map for region " region-id "with polygons" (map :polygon-id facilities))
        {}))))

(defrecord MapsService [config runner regions])

(defn maps-service
  "Construct a Maps Service component from config"
  [config]
  (map->MapsService {:config config}))
