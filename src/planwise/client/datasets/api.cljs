(ns planwise.client.datasets.api
  (:require [ajax.core :refer [GET POST DELETE]]
            [planwise.client.api :refer [json-request]]))

;; ----------------------------------------------------------------------------
;; Utility functions

(defn- map-server-status
  [server-status]
  (some-> server-status
          (update :status keyword)
          (update :state keyword)))

(defn- map-dataset
  [server-dataset]
  (update server-dataset :server-status map-server-status))

;; ----------------------------------------------------------------------------
;; API methods

(defn load-datasets
  [& handlers]
  (GET "/api/datasets"
      (json-request {} handlers :mapper-fn (partial map map-dataset))))

(defn load-dataset
  [dataset-id & handlers]
  (GET (str "/api/datasets/" dataset-id)
      (json-request {} handlers :mapper-fn map-dataset)))

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
                    handlers
                    :mapper-fn map-dataset)))

(defn update-dataset!
  [id & handlers]
  (POST (str "/api/datasets/" id "/update")
    (json-request {} handlers :mapper-fn map-dataset)))

(defn cancel-import!
  [dataset-id & handlers]
  (POST "/api/datasets/cancel"
      (json-request {:dataset-id dataset-id} handlers :mapper-fn (partial map map-dataset))))

(defn delete-dataset!
  [dataset-id & handlers]
  (DELETE (str "/api/datasets/" dataset-id)
      (json-request {} handlers)))
