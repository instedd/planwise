(ns planwise.client.projects.api
  (:require [ajax.core :refer [GET POST DELETE]]
            [planwise.client.api :refer [json-request]]))

(defn load-projects [& handlers]
  (GET
    "/api/projects/"
    (json-request {} handlers)))

(defn create-project [params & handlers]
  (POST
    "/api/projects/"
    (json-request params handlers)))

(defn delete-project [id & handlers]
  (DELETE
    (str "/api/projects/" id)
    (json-request {:id id} handlers)))

(defn leave-project [id & handlers]
  (DELETE
    (str "/api/projects/" id "/access")
    (json-request {:id id} handlers)))
