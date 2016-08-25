(ns planwise.boundary.datasets
  (:require [schema.core :as s]
            [planwise.component.datasets :as service]))

(defprotocol Datasets
  "API for managing datasets."

  (list-datasets-for-user [this user-id]
    "Returns datasets owned by the user.")

  (create-dataset! [this dataset]
    "Creates a new dataset.")

  (find-dataset [this dataset-id]
    "Retrieves the dataset by ID, returning nil if not found."))

;; Reference implementation

(extend-protocol Datasets
  planwise.component.datasets.DatasetsStore
  (list-datasets-for-user [store user-id]
    (service/list-datasets-for-user store user-id))
  (create-dataset! [store dataset]
    (service/create-dataset! store dataset))
  (find-dataset [store dataset-id]
    (service/find-dataset store dataset-id)))
