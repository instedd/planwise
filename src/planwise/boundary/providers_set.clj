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

  (get-providers-with-coverage-in-region [this provider-set-id version filter-options]
    "Retrieves the providers for a version of a provider-set located inside the region")

  (count-providers-filter-by-tags
    [this provider-set-id region-id tags]
    [this provider-set-id region-id tags version]
    "Count providers filtered by a single tag. Default version is the last one")

  (get-coverage [this provider-id algorithm filter-options]
    "Finds the provider's coverage for an algorithm and filter options"))


;; Preprocessing provider-set job type: ::preprocess-provider-set
