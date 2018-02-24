(ns planwise.boundary.maps)

(defprotocol Maps
  "Mapping utilities"

  (mapserver-url [service]
    "Retrieve the demographics tile URL template")

  (default-capacity [service]
    "Retrieves the default capacity of a facility for calculating coverage")

  (calculate-demand? [service]
    "Feature toggle flag for unsatisfied emand calculation")

  (demand-map [service region-id polygons]
    "Returns the key for an unsatsified demand tile layer and the total unsatisfied demand,
     for the specified region and using the chosen facility polygons"))

