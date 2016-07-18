(ns planwise.endpoint.projects
  (:require [compojure.core :refer :all]
            [ring.util.response :refer [content-type response not-found]]
            [clojure.data.json :as json]
            [buddy.auth :refer [authenticated?]]
            [buddy.auth.accessrules :refer [restrict]]
            [planwise.boundary.projects :as projects]))

(defn- endpoint-routes [service]
  (routes
   (GET "/" []
     (let [projects (projects/list-projects service)]
       (response projects)))

   (GET "/:id" [id]
     (if-let [project (projects/get-project service (Integer/parseInt id))]
       (response project)
       (not-found {:error "Project not found"})))

   (POST "/" [goal]
     (response (projects/create-project service {:goal goal})))))

(defn projects-endpoint [{service :projects}]
  (context "/api/projects" []
    (restrict (endpoint-routes service) {:handler authenticated?})))
