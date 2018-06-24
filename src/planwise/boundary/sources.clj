(ns planwise.boundary.sources)

(defprotocol Sources
  "API for manipulating sources"

  (list-sources [this]
    "Returns the list of the sources")

  (import-from-csv [this options csv-file]
    "Import (create) with sources (type Point)"))
