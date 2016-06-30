(ns planwise.boundary.routing
  (:require [planwise.component.routing :as service]))

(defprotocol Routing
  "API for routing related functions"

  (nearest-node [this lat lon]
    "Find the node in the routing graph nearest to the point (lat,lon)")

  (compute-isochrone [this node-id distance algorithm]
    "Compute the isochrone (using algorithm) from the given node using distance
    as the threshold"))


(extend-protocol Routing
  planwise.component.routing.RoutingService
  (nearest-node [this lat lon]
    (service/nearest-node this lat lon))
  (compute-isochrone [this node-id distance algorithm]
    (service/isochrone this node-id distance algorithm)))
