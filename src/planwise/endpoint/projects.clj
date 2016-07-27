(ns planwise.endpoint.projects
  (:require [compojure.core :refer :all]
            [ring.util.response :refer [content-type response status not-found]]
            [clojure.data.json :as json]
            [buddy.auth :refer [authenticated?]]
            [buddy.auth.accessrules :refer [restrict]]
            [planwise.boundary.projects :as projects]
            [planwise.boundary.facilities :as facilities]
            [clojure.walk :refer [keywordize-keys]]
            [clojure.string :as str]))

(defn- assoc-extra-data
  [with project facilities]
  (let [criteria {:region (:region-id project)
                  :types (get-in project [:filters :facilities :type])}]
    (case with
      :facilities
      (let [project-facilities (facilities/list-facilities facilities criteria)]
        (assoc project :facilities project-facilities))
      :isochrones
      (let [time (get-in project [:filters :transport :time])
            options {:threshold time}
            isochrones (when time
                         (facilities/list-with-isochrones facilities options criteria))]
        (assoc project :isochrones isochrones))
      project)))

(defn- endpoint-routes
  [{service :projects facilities :facilities}]
  (routes
   (GET "/" []
     (let [projects (projects/list-projects service)]
       (response projects)))

   (GET "/:id" [id with]
     (if-let [project (projects/get-project service (Integer/parseInt id))]
       (response (assoc-extra-data (keyword with) project facilities))
       (not-found {:error "Project not found"})))

   (PUT "/:id" [id filters with]
     (let [id (Integer/parseInt id)
           filters (keywordize-keys filters)]
       (if-let [project (projects/update-project service {:id id :filters filters})]
         (response (assoc-extra-data (keyword with) project facilities))
         (-> (response {:status "failure"})
             (status 400)))))

   (POST "/" [goal region-id]
     (let [goal (str/trim goal)
           region-id (Integer. region-id)]
       (response (projects/create-project service {:goal goal, :region-id region-id}))))

   (DELETE "/:id" [id]
     (if-let [project (projects/delete-project service (Integer/parseInt id))]
       (response {:deleted id})
       (not-found {:error "Project not found"})))))

(defn projects-endpoint [endpoint]
  (context "/api/projects" []
    (restrict (endpoint-routes endpoint) {:handler authenticated?})))
