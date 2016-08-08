(ns planwise.component.maps
  (:require [com.stuartsierra.component :as component]
            [clojure.string :as str]
            [taoensso.timbre :as timbre]
            [clojure.java.shell :refer [sh]]))

(timbre/refer-timbre)

(defn mapserver-url
  [service]
  (:mapserver-url service))

(defn- demand-map-key
  [region-id polygon-ids]
  (str/join "_" (cons region-id polygon-ids)))

(defn- default-capacity
  [service]
  (:facilities-capacity service))

(defn- bin-path
  [service & args]
  (apply str (:bin-path service) args))

(defn- data-path
  [service & args]
  (apply str (:data-path service) args))

(defn- demands-path
  [service & args]
  (apply data-path service (cons "demands/" args)))

(defn- populations-path
  [service & args]
  (apply data-path service (cons "populations/" args)))

(defn- isochrones-path
  [service & args]
  (apply data-path service (cons "isochrones/" args)))

(defn demand-map
  [service region-id facilities]
  (let [polygons (filter :polygon-id facilities)
        map-key  (demand-map-key region-id (map :polygon-id polygons))
        args     (->> polygons
                    (map (juxt
                            #(isochrones-path service region-id "/" (:polygon-id %) ".tif")
                            #(str (or (:capacity %) (default-capacity service)))))
                    (flatten)
                    (concat [(bin-path service "calculate-demand")
                             (demands-path service map-key ".tif")
                             (populations-path service region-id ".tif")])
                    (vec))
        _        (info "Invoking " (str/join " " args))
        response (apply sh args)]
    (case (:exit response)
      0 { :map-key map-key,
          :unsatisfied-count (-> response
                               (:out)
                               (str/trim)
                               (Integer.))}
      (throw (RuntimeException. (str "Error calculating demand map:\n" (:err response)))))))


(defrecord MapsService [config])

(defn maps-service
  "Construct a Maps Service component"
  [config]
  (map->MapsService config))
