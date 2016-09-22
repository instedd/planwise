(ns planwise.boundary.datasets
  (:require [schema.core :as s]
            [planwise.component.datasets :as service]))

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

(defn owned-by?
  [dataset user-id]
  (= user-id (:owner-id dataset)))

;; Reference implementation

(extend-protocol Datasets
  planwise.component.datasets.DatasetsStore
  (list-datasets-for-user [store user-id]
    (service/list-datasets-for-user store user-id))
  (list-datasets-with-import-jobs [store]
    (service/list-datasets-with-import-jobs store))
  (create-dataset! [store dataset]
    (service/create-dataset! store dataset))
  (find-dataset [store dataset-id]
    (service/find-dataset store dataset-id))
  (update-dataset [store dataset]
    (service/update-dataset store dataset))
  (destroy-dataset! [store dataset-id]
    (service/destroy-dataset! store dataset-id))
  (accessible-by? [store dataset user-id]
    (service/accessible-by? store dataset user-id)))
