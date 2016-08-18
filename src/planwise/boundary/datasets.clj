(ns planwise.boundary.datasets
  (:require [schema.core :as s]
            [planwise.component.datasets :as service]))

(defprotocol Datasets
  "API for managing datasets."

  (list-datasets-for-user [this user-id]
    "Returns datasets owned by the user."))

;; Reference implementation

(extend-protocol Datasets
  planwise.component.datasets.DatasetsStore
  (list-datasets-for-user [store user-id]
    (service/list-datasets-for-user store user-id)))
