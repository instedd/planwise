(ns planwise.boundary.resmap
  (:require [planwise.component.resmap :as service]))

(defprotocol Resmap
  "API for resource-map integration related functions"

  (get-collection-sites
    [service user-ident coll-id params]
    "Returns the list of sites for a given collection")

  (find-collection-field
    [service user-ident coll-id field-id]
    "Returns a collection field given its id"))

(extend-protocol Resmap
  planwise.component.resmap.ResmapClient
  (get-collection-sites [this user-ident coll-id params]
    (service/get-collection-sites this user-ident coll-id params))
  (find-collection-field [this user-ident coll-id field-id]
    (service/find-collection-field this user-ident coll-id field-id)))
