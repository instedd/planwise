(ns planwise.boundary.datasets2)

(defprotocol Datasets2
  "API for manipulating site datasets"

  (list-datasets [this owner-id]
    "Returns the list of the site datasets owned by the user")

  (get-dataset [this dataset-id]
    "Finds a site dataset by id")

  (create-dataset [this name owner-id]
    "Creates a site dataset for the given owner with the name")

  (import-csv [this dataset-id csv-file]
    "Import a CSV file and add the sites to the dataset")

  (create-and-import-sites [this name owner-id csv-file]
    "Create and import a CSV file atomically"))
