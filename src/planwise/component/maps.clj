(ns planwise.component.maps
  (:require [com.stuartsierra.component :as component]
            [planwise.component.runner :refer [run-external]]
            [clojure.string :as str]
            [digest :as digest]
            [taoensso.timbre :as timbre]))

(timbre/refer-timbre)

(defn mapserver-url
  [{config :config}]
  (:mapserver-url config))

(defn- demand-map-key
  [region-id polygon-ids]
  (digest/sha-256
    (str/join "_" (cons region-id polygon-ids))))

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
  (apply data-path service (cons "populations/" args)))

(defn- isochrones-path
  [service & args]
  (apply data-path service (cons "isochrones/" args)))

(defn- capacity-for
  [service {:keys [population population-in-region]}]
  (let [capacity (default-capacity service)
        factor   (if population-in-region (/ population-in-region population) 1)]
    (int (* factor capacity))))

(defn demand-map
  [service region-id facilities]
  (let [polygons (filter :polygon-id facilities)
        map-key  (demand-map-key region-id (map :polygon-id polygons))
        args     (->> polygons
                    (map (juxt
                            #(isochrones-path service region-id "/" (:polygon-id %) ".tif")
                            #(str (capacity-for service %))))
                    (flatten)
                    (concat [(demands-path service map-key ".tif")
                             (populations-path service region-id ".tif")])
                    (vec))
        response          (apply run-external (:runner service) :bin "calculate-demand" args)
        unsatisfied-count (-> response
                            (str/trim-newline)
                            (str/trim)
                            (Integer.))]
      {:map-key map-key,
       :unsatisfied-count unsatisfied-count}))

(defrecord MapsService [config runner])

(defn maps-service
  "Construct a Maps Service component from config"
  [config]
  (map->MapsService {:config config}))
