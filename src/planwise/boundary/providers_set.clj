(ns planwise.boundary.providers-set)

(defprotocol ProvidersSet
  "API for manipulating providers set"

  (list-providers-set [this owner-id]
    "Returns the list of the providers-set owned by the user")

  (get-provider-set [this provider-set-id]
    "Finds a provider-set by id")

  (get-provider [this provider-id]
    "Finds provider by id")

  (create-and-import-providers [this options csv-file]
    "Create and import a CSV file atomically")

  (new-processing-job [this provider-set-id]
    "Creates a new job state for processing the provider-set asynchronously")

  (get-providers-in-region [this provider-set-id version filter-options]
    "Retrieves providers and disabled-providers for a version of a provider-set located inside the region")

  (count-providers-filter-by-tags
    [this provider-set-id region-id tags]
    [this provider-set-id region-id tags version]
    "Count providers filtered by a single tag. Default version is the last one")

  (get-radius-from-computed-coverage
    [this criteria provider-set-id]
    "Given a coverage criteria returns the average of maximus distances")

  (get-coverage [this provider-id coverage-options]
    "Finds the provider's coverage
     coverage-options must contain algorithm and filter-options and optionally
     a region-id to clip the returned geometry.
     Returns the result as GeoJSON string")

  (delete-provider-set
    [this provider-set-id]
    "Delete provider-set given a provider-set-id.
     Providers and providers coverage referenced from provider-set are also deleted.
     When provider set is referenced from valid project an exception is thrown.
     If provider set is deleted all created files are deleted."))

;; Preprocessing provider-set job type: ::preprocess-provider-set
