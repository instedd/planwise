(ns planwise.boundary.sources)

(defprotocol Sources
  "API for manipulating sources"

  (list-sources [this owner-id]
    "Returns the list of the sources")

  (import-from-csv [this options csv-file]
    "Import (create) with sources (type Point)")

  (get-source-set-by-id [this id]
    "Finds a sources-set by id")

  (list-sources-under-provider-coverage [this source-set-id provider-id algorithm filter-options]
    "Finds sources under a provider coverage")

  (list-sources-in-set [this source-set-id]
    "Returns the list of sources in a source-set"))
