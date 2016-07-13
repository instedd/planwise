(ns planwise.client.projects.api
  (:require [ajax.core :refer [GET POST]]
            [planwise.client.api :refer [json-request]]))

(defn load-project [id & extras]
  (GET
    (str "/api/projects/" id)
    (json-request {:id id} extras)))

(defn create-project [params & extras]
  (POST
    "/api/projects/"
    (json-request params extras)))
