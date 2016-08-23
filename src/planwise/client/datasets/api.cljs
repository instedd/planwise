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

;; Old functions
;; TODO: review

(defn load-datasets-info
  [& handlers]
  (GET "/api/datasets/info"
      (json-request {} handlers)))

(defn load-collection-info
  [coll-id & handlers]
  (GET (str "/api/datasets/collection-info/" coll-id)
      (json-request {} handlers)))

(defn import-collection!
  [coll-id type-field & handlers]
  (POST "/api/datasets/import"
      (json-request {:coll-id coll-id
                     :type-field type-field}
                    handlers)))

(defn importer-status
  [& handlers]
  (GET "/api/datasets/status"
      (json-request {} handlers)))

(defn cancel-import!
  [& handlers]
  (POST "/api/datasets/cancel"
      (json-request {} handlers)))
