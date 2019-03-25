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

  (get-providers-in-region [this provider-set-id version filter-options]
    "Retrieves providers and disabled-providers for a version of a provider-set located inside the region")

  (count-providers-filter-by-tags
    [this provider-set-id region-id tags]
    [this provider-set-id region-id tags version]
    "Count providers filtered by a single tag. Default version is the last one")

  (delete-provider-set
    [this provider-set-id]
    "Delete provider-set given a provider-set-id.
     Providers and providers coverage referenced from provider-set are also deleted.
     When provider set is referenced from valid project an exception is thrown.
     If provider set is deleted all created files are deleted."))

