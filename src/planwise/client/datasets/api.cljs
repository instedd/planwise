(ns planwise.client.datasets.api
  (:require [ajax.core :refer [GET POST]]
            [planwise.client.api :refer [json-request]]))


(defn load-datasets
  [& handlers]
  (GET "/api/datasets"
      (json-request {} handlers)))

(defn load-resourcemap-info
  [& handlers]
  (GET "/api/datasets/resourcemap-info"
      (json-request {} handlers)))

(defn create-dataset!
  [name description coll-id type-field & handlers]
  (POST "/api/datasets"
      (json-request {:name name
                     :description description
                     :coll-id coll-id
                     :type-field type-field}
                    handlers)))

(defn cancel-import!
  [dataset-id & handlers]
  (POST "/api/datasets/cancel"
      (json-request {:dataset-id dataset-id} handlers)))
