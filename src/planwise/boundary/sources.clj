(ns planwise.boundary.sources)

(defprotocol Sources
  "API for manipulating sources"

  (list-sources [this owner-id]
    "Returns the list of the sources")

  (import-from-csv [this options csv-file]
    "Import (create) with sources (type Point)")

  (get-source-set-by-id [this id]
    "Finds a sources-set by id")

  (list-sources-in-set [this source-set-id]
    "Returns the list of sources in a source-set")

  (get-sources-from-set-in-region [this source-set-id region-id]
    "Returns a list of sources in a source set that are inside the given region")

  (enum-sources-under-provider-coverage [this source-set-id provider-coverage-id]
    "Returns the ids of the sources from the source set covered by the provider coverage")

  (enum-sources-under-geojson-coverage [this source-set-id coverage-geojson]
    "Returns the ids of the sources from the source set contained in the given GeoJSON")

  (enum-sources-under-coverage [this source-set-id coverage-geom]
    "Returns the ids of the sources from the source set contained in the given geometry"))
