(ns planwise.component.maps
  (:require [com.stuartsierra.component :as component]))

(defn demo-tile-url
  "Retrieve the demographics tile URL template"
  [service]
  (:demo-tile-url service))

(defrecord MapsService [config])

(defn maps-service
  "Construct a Maps Service component"
  [config]
  (map->MapsService config))
