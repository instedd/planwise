(ns planwise.client.analyses.api
  (:require [ajax.core :refer [GET POST DELETE]]
            [planwise.client.api :refer [json-request]]))

;; ----------------------------------------------------------------------------
;; Utility functions


;; ----------------------------------------------------------------------------
;; API methods

(defn load-analyses
  [& handlers]
  (GET "/api/analyses"
      (json-request {} handlers)))

(defn create-analysis!
  [name & handlers]
  (POST "/api/analyses"
      (json-request {:name name}
                    handlers)))
