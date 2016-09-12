(ns planwise.boundary.maps
  (:require [planwise.component.maps :as service]))

(defprotocol Maps
  "Mapping utilities"

  (mapserver-url [service]
    "Retrieve the demographics tile URL template")

  (default-capacity [service]
    "Retrieves the default capacity of a facility for calculating coverage")

  (demand-map [service region-id polygons]
    "Returns the key for an unsatsified demand tile layer and the total unsatisfied demand,
     for the specified region and using the chosen facility polygons"))

;; Reference implementation

(extend-protocol Maps
  planwise.component.maps.MapsService
  (mapserver-url [service]
    (service/mapserver-url service))
  (default-capacity [service]
    (service/default-capacity service))
  (demand-map [service region-id polygons]
    (service/demand-map service region-id polygons)))
