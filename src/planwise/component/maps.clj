(ns planwise.component.maps
  (:require [com.stuartsierra.component :as component]
            [clojure.string :as str]
            [taoensso.timbre :as timbre]
            [clojure.java.shell :refer [sh]]))

(timbre/refer-timbre)

;; TODO: Load from config
(def bin-path "cpp/")
(def data-path "data/")
(def demands-path "data/demands/")
(def populations-path "data/populations/")
(def isochrones-path "data/isochrones/")
(def default-capacity 200000)

(defn- demand-map-key
  [region-id polygon-ids]
  (str/join "_" (cons region-id polygon-ids)))

(defn mapserver-url
  [service]
  (:mapserver-url service))

(defn demand-map
  [service region-id facilities]
  (let [polygons (filter :polygon-id facilities)
        map-key  (demand-map-key region-id (map :polygon-id polygons))
        args     (->> polygons
                    (map (juxt
                            #(str isochrones-path region-id "/" (:polygon-id %) ".tif")
                            #(str (or (:capacity %) default-capacity))))
                    (flatten)
                    (concat [(str bin-path "calculate-demand")
                             (str demands-path map-key ".tif")
                             (str populations-path region-id ".tif")])
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
