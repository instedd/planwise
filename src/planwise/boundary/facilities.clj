(ns planwise.boundary.facilities
  (:require [planwise.component.facilities :as service]))

(defprotocol Facilities
  "API for reading facilities and related information."

  (list-facilities
    [this dataset-id]
    [this dataset-id criteria]
    "List the facilities that match the supplied criteria (:types and :region)")

  (isochrones-in-bbox
    [this dataset-id isochrone-options facilities-criteria]
    "Returns the isochrones present in the :bbox specified in the criteria param,
     returning only isochrone, id and polygon-id. An :excluding parameter can be
     also provided; for such facilities the isochrones will not be returned,
     yet their id will still be returned.")

  (list-types [this dataset-id]
    "Lists all the facility types in the dataset."))


;; Reference implementation

(extend-protocol Facilities
  planwise.component.facilities.FacilitiesService
  (list-facilities
    ([service dataset-id]
     (service/list-facilities service dataset-id))
    ([service dataset-id criteria]
     (service/list-facilities service dataset-id criteria)))
  (isochrones-in-bbox [service dataset-id isochrone-options facilities-criteria]
    (service/isochrones-in-bbox service dataset-id isochrone-options facilities-criteria))
  (list-types [service dataset-id]
    (service/list-types service dataset-id)))
