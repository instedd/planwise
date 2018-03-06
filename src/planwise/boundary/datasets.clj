(ns planwise.boundary.datasets)

(defprotocol Datasets
  "API for managing datasets."

  (list-datasets-for-user [this user-id]
    "Returns datasets owned by the user.")

  (list-datasets-with-import-jobs [this]
    "Returns datasets that have a pending import job.")

  (create-dataset! [this dataset]
    "Creates a new dataset.")

  (find-dataset [this dataset-id]
    "Retrieves the dataset by ID, returning nil if not found.")

  (update-dataset [this dataset]
    "Updates fields in the dataset")

  (destroy-dataset! [this dataset-id]
    "Destroy a dataset")

  (accessible-by? [this dataset user-id]
    "Returns whether a user can access a dataset, if he has access to a project
     that uses the dataset."))

