(ns planwise.boundary.facilities)

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

  (polygons-in-region
   [this dataset-id isochrone-options criteria]
   "Returns the facilities polygons in the criteria's :region, for the facilities
    that satisfy the specified criteria. Includes fields :facility-polygon-id,
    :facility-population, :facility-region-population and :capacity.")

  (list-types [this dataset-id]
    "Lists all the facility types in the dataset.")

  (count-facilities [this dataset-id criteria])

  (insert-types! [this dataset-id types])

  (insert-facilities! [this dataset-id facilities])

  (preprocess-isochrones
    [this]
    [this id])

  (clear-facilities-processed-status! [this])

  (destroy-facilities! [this dataset-id options])

  (destroy-types! [this dataset-id options]))
