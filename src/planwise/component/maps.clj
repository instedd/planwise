(ns planwise.component.maps
  (:require [planwise.boundary.maps :as boundary]
            [integrant.core :as ig]
            [taoensso.timbre :as timbre]))

(timbre/refer-timbre)

(defn mapserver-url
  [{config :config}]
  (:mapserver-url config))

(defn- data-path
  [{config :config} & args]
  (apply str (:data config) args))

(defrecord MapsService [config]
  boundary/Maps
  (mapserver-url [service]
    (mapserver-url service)))

(defmethod ig/init-key :planwise.component/maps
  [_ config]
  (map->MapsService config))
