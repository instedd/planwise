(ns planwise.endpoint.projects
  (:require [compojure.core :refer :all]
            [ring.util.response :refer [content-type response]]
            [clojure.data.json :as json]
            [planwise.boundary.projects :as projects]))

(defn projects-endpoint [{service :projects}]
  (context "/projects" []

    (GET "/" []
      (let [projects (projects/list-projects service)]
        (response projects)))

    (GET "/:id/details" [id]
      (if-let [project (projects/get-project service (Integer/parseInt id))]
        (response project)
        (throw "not-found")))

    (POST "/" [goal]
      (response (projects/create-project service {:goal goal})))))
