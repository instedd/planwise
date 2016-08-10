(ns planwise.boundary.maps
  (:require [planwise.component.maps :as service]))

(defprotocol Maps
  "Mapping utilities"

  (mapserver-url [service]
    "Retrieve the demographics tile URL template")

  (demand-map [service region-id polygons]
    "Returns the key for an unsatsified demand tile layer and the total unsatisfied demand,
     for the specified region and using the chosen facility polygons"))

;; Reference implementation

(extend-protocol Maps
  planwise.component.maps.MapsService
  (mapserver-url [service]
    (service/mapserver-url service))
  (demand-map [service region-id polygons]
    (service/demand-map service region-id polygons)))
