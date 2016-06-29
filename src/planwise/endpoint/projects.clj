(ns planwise.endpoint.projects
  (:require [compojure.core :refer :all]
            [ring.util.response :refer [content-type response]]
            [clojure.data.json :as json]
            [planwise.projects.core :as projects]))

(defn projects-endpoint [{{db :spec} :db}]
  (context "/projects" []

    (GET "/" []
      (let [projects (projects/select-projects db)]
        (-> (response (json/write-str projects))
            (content-type "application/json"))))

    (POST "/" [goal]
      (projects/create-project db {:goal goal}))))
