(ns planwise.boundary.facilities
  (:require [planwise.component.facilities :as service]))

(defprotocol Facilities
  "API for reading facilities and related information."

  (list-facilities
    [this]
    [this criteria]
    "List the facilities that match the supplied criteria (:types and :region)")

  (list-with-isochrones
    [this]
    [this isochrone-options]
    [this isochrone-options facilities-criteria]
    "List the facilities with their corresponding catchment areas for the given
    threshold (in seconds) calculated using algorithm and simplified according
    to the given parameter.")

  (isochrone-all-facilities [this threshold]
    "Retrieve the catchment area for all facilities available for the given
    threshold.")

  (list-types [this]
    "Lists all the facility types."))


;; Reference implementation

(extend-protocol Facilities
  planwise.component.facilities.FacilitiesService
  (list-facilities
    ([service]
     (service/list-facilities service))
    ([service criteria]
     (service/list-facilities service criteria)))
  (list-with-isochrones
    ([service]
     (service/list-with-isochrones service))
    ([service isochrone-options]
     (service/list-with-isochrones service isochrone-options))
    ([service isochrone-options facilities-criteria]
     (service/list-with-isochrones service isochrone-options facilities-criteria)))
  (isochrone-all-facilities [service threshold]
    (service/get-isochrone-for-all-facilities service threshold))
  (list-types [service]
    (service/list-types service)))
