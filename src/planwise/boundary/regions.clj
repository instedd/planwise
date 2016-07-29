(ns planwise.boundary.regions
  (:require [planwise.component.regions :as service]))

(defprotocol Regions
  "API for managing regions."

  (list-regions [this]
    "Returns all regions in the database.")

  (list-regions-with-preview [this ids]
    "Returns regions including a preview geojson of their boundaries given their ids.")

  (list-regions-with-geo [this ids simplify]
    "Returns regions including a simplified geojson with their boundaries given their ids."))

;; Reference implementation

(extend-protocol Regions
  planwise.component.regions.RegionsService
  (list-regions [service]
    (service/list-regions service))
  (list-regions-with-preview [service ids]
    (service/list-regions-with-preview service ids))
  (list-regions-with-geo [service ids simplify]
    (service/list-regions-with-geo service ids simplify)))
