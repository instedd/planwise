(ns planwise.boundary.datasets
  (:require [schema.core :as s]
            [planwise.component.datasets :as service]))

(defprotocol Datasets
  "API for managing datasets."

  (list-datasets-for-user [this user-id]
    "Returns datasets owned by the user.")

  (create-dataset! [this dataset]
    "Creates a new dataset."))

;; Reference implementation

(extend-protocol Datasets
  planwise.component.datasets.DatasetsStore
  (list-datasets-for-user [store user-id]
    (service/list-datasets-for-user store user-id))
  (create-dataset! [store dataset]
    (service/create-dataset! store dataset)))
