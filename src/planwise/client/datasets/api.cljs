(ns planwise.client.datasets.api
  (:require [ajax.core :refer [GET]]
            [planwise.client.api :refer [json-request]]))


(defn load-datasets-info
  [& handlers]
  (GET "/api/datasets/info"
      (json-request {} handlers)))
