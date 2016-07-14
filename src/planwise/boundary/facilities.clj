(ns planwise.boundary.facilities
  (:require [planwise.component.facilities :as service]))

(defprotocol Facilities
  "API for reading facilities and related information."

  (list-facilities [this]
    "List the facilities currently available")

  (list-facilities-with-types [this types]
    "List the facilities that have the corresponding types")

  (list-with-isochrones [this threshold algorithm simplify]
    "List the facilities with their corresponding catchment areas for the given
    threshold (in seconds) calculated using algorithm and simplified according
    to the given parameter.")

  (isochrone-all-facilities [this threshold]
    "Retrieve the catchment area for all facilities available for the given
    threshold."))


;; Reference implementation

(extend-protocol Facilities
  planwise.component.facilities.FacilitiesService
  (list-facilities [service]
    (service/list-facilities service))
  (list-facilities-with-types [service types]
    (service/list-facilities-with-types service types))
  (list-with-isochrones [service threshold algorithm simplify]
    (service/list-with-isochrones service threshold algorithm simplify))
  (isochrone-all-facilities [service threshold]
    (service/get-isochrone-for-all-facilities service threshold)))
