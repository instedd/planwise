(ns planwise.boundary.providers-set)

(defprotocol Providers-Set
  "API for manipulating providers set"

  (list-provider-sets [this owner-id]
    "Returns the list of the site provider-set owned by the user")

  (get-provider-set [this provider-set-id]
    "Finds a site provider-set by id")

  (create-and-import-sites [this options csv-file]
    "Create and import a CSV file atomically")

  (new-processing-job [this provider-set-id]
    "Creates a new job state for processing the provider-set asynchronously")

  (get-sites-with-coverage-in-region [this provider-set-id version filter-options]
    "Retrieves the sites for a version of a provider-set located inside the region")


  (count-sites-filter-by-tags
    [this provider-set-id region-id tags]
    [this provider-set-id region-id tags version]
    "Count sites filtered by a single tag. Default version is the last one"))

;; Preprocessing provider-set job type: ::preprocess-dataset
