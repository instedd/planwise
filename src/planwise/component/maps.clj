(ns planwise.component.maps
  (:require [planwise.boundary.maps :as boundary]
            [planwise.boundary.runner :as runner]
            [planwise.boundary.regions :as regions]
            [integrant.core :as ig]
            [clojure.string :as str]
            [clojure.java.io :as io]
            [planwise.util.str :refer [trim-to-int]]
            [digest :as digest]
            [taoensso.timbre :as timbre]))

(timbre/refer-timbre)

(def scale-saturation
  1001.0)

(defn mapserver-url
  [{config :config}]
  (:mapserver-url config))

(defn calculate-demand?
  [{config :config}]
  (boolean (:calculate-demand config)))

(defn calculate-demand-for-region?
  [service {admin-level :admin-level}]
  (and
    (calculate-demand? service)
    (> admin-level 2)))

(defn- demand-map-key
  [region-id polygons-with-capacities]
  (digest/sha-256
    (str/join "_" (cons region-id polygons-with-capacities))))

(defn default-capacity
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
  [service {:keys [population population-in-region capacity]}]
  (let [capacity (or capacity (default-capacity service))
        factor   (/ population-in-region population)]
    (int (* factor capacity))))

(defn- region-saturation
  [{raster-pixel-area :raster-pixel-area}]
  (/
    (* scale-saturation raster-pixel-area)
    1000000.0))

(defn- setup-demands-folder
  [service]
  (let [path (demands-path service "-")]
    (clojure.java.io/make-parents path)))

(defn- write-args-file
  [service map-key & args]
  (let [filename (demands-path service map-key ".args")]
    (spit filename (str/join " " args))
    filename))

(defn- facilities->args
  [service region-id facilities]
  (->> facilities
    (filter :polygon-id)
    (filter :population)
    (filter (comp pos? :population))
    (filter :population-in-region)
    (map (juxt
          #(isochrones-path service region-id "/" (:polygon-id %) ".tif")
          #(str (capacity-for service %))))
    (flatten)))

(defn demand-map
  [service region-id facilities]
  (let [region (regions/find-region (:regions service) region-id)]
    (when (calculate-demand-for-region? service region)
      (try
        (let [polygons-args (facilities->args service region-id facilities)
              map-key       (demand-map-key region-id polygons-args)
              saturation    (format "%.2f" (region-saturation region))

              _          (setup-demands-folder service)
              args-fname (apply write-args-file service map-key
                           (demands-path service map-key ".tif")
                           (str saturation)
                           (populations-path service region-id ".tif")
                           (vec polygons-args))
              response   (runner/run-external
                            (:runner service)
                            :bin
                            300000
                            "calculate-demand"
                            [(str "@" args-fname)])]
            {:map-key map-key,
             :unsatisfied-count (trim-to-int response)})
        (catch Exception e
          (error e "Error calculating demand map for region " region-id "with polygons" (map :polygon-id facilities))
          {})))))

(defrecord MapsService [config runner regions]
  boundary/Maps
  (mapserver-url [service]
    (mapserver-url service))
  (default-capacity [service]
    (default-capacity service))
  (calculate-demand? [service]
    (calculate-demand? service))
  (demand-map [service region-id polygons]
    (demand-map service region-id polygons)))

(defmethod ig/init-key :planwise.component/maps
  [_ config]
  (map->MapsService config))
