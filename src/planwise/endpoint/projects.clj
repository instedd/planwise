(ns planwise.endpoint.projects
  (:require [compojure.core :refer :all]
            [ring.util.response :refer [content-type response]]
            [clojure.data.json :as json]
            [planwise.boundary.projects :as projects]))

(defn projects-endpoint [{service :projects}]
  (context "/projects" []

    (GET "/" []
      (let [projects (projects/select-projects service)]
        (-> (response (json/write-str projects))
            (content-type "application/json"))))

    (POST "/" [goal]
      (response (projects/create-project service {:goal goal})))))
