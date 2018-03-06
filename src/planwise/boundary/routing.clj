(ns planwise.boundary.routing)

(defprotocol Routing
  "API for routing related functions"

  (nearest-node [this lat lon]
    "Find the node in the routing graph nearest to the point (lat,lon)")

  (compute-isochrone [this node-id distance algorithm]
    "Compute the isochrone (using algorithm) from the given node using distance
    as the threshold"))
