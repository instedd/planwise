(ns planwise.boundary.datasets2)

(defprotocol Datasets2
  "API for manipulating site datasets"

  (list-datasets [this owner-id]
    "Returns the list of the site datasets owned by the user")

  (get-dataset [this dataset-id]
    "Finds a site dataset by id")

  (create-and-import-sites [this options csv-file]
    "Create and import a CSV file atomically")

  (new-processing-job [this dataset-id]
    "Creates a new job state for processing the dataset asynchronously"))

;; Preprocessing dataset job type: ::preprocess-dataset
