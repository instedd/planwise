(ns planwise.boundary.resmap)

(defprotocol Resmap
  "API for resource-map integration related functions"

  (get-collection-sites
    [service user-ident coll-id params]
    "Returns the list of sites for a given collection")

  (find-collection-field
    [service user-ident coll-id field-id]
    "Returns a collection field given its id"))

