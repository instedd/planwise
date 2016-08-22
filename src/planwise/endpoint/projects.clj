(ns planwise.endpoint.projects
  (:require [compojure.core :refer :all]
            [ring.util.response :refer [content-type response status not-found]]
            [clojure.data.json :as json]
            [planwise.boundary.maps :as maps]
            [buddy.auth :refer [authenticated?]]
            [buddy.auth.accessrules :refer [restrict]]
            [planwise.util.ring :as util]
            [planwise.boundary.projects :as projects]
            [planwise.boundary.facilities :as facilities]
            [clojure.walk :refer [keywordize-keys]]
            [clojure.string :as str]))

(defn- assoc-extra-data
  [with project {:keys [facilities maps]}]
  (let [criteria {:region (:region-id project)
                  :types (or (get-in project [:filters :facilities :type]) [])}]
    (case with
      :facilities
      (let [project-facilities (facilities/list-facilities facilities criteria)]
        (assoc project :facilities project-facilities))
      :facilities-with-demand
      (let [project-facilities (facilities/list-facilities facilities criteria)
            time       (get-in project [:filters :transport :time])
            isochrones (when time
                         []) ; TODO: Load isochrones to calculate demand
            demand     (when isochrones
                         (maps/demand-map maps (:region-id project) isochrones))]
        (-> project
          (merge demand)
          (assoc :facilities project-facilities)))
      project)))

(defn- endpoint-routes
  [{service :projects facilities :facilities, :as services}]
  (routes
   (GET "/" request
     (let [user-id (util/request-user-id request)
           projects (projects/list-projects-for-user service user-id)]
       (response projects)))

   (GET "/:id" [id with :as request]
     (let [user-id (util/request-user-id request)
           project (projects/get-project service (Integer. id))]
       (if (projects/accessible-by? project user-id)
         (response (assoc-extra-data (keyword with) project services))
         (not-found {:error "Project not found"}))))

   (PUT "/:id" [id filters with :as request]
     (let [id (Integer. id)
           filters (keywordize-keys filters)
           user-id (util/request-user-id request)
           project (projects/get-project service id)]
       (if (projects/owned-by? project user-id)
         (if-let [project (projects/update-project service {:id id :filters filters})]
           (response (assoc-extra-data (keyword with) project services))
           (-> (response {:status "failure"})
               (status 400)))
         (not-found {:error "Project not found"}))))

   (POST "/" [goal region-id :as request]
     (let [goal (str/trim goal)
           region-id (Integer. region-id)
           user-id (util/request-user-id request)
           project-templ {:goal goal
                          :region-id region-id
                          :owner-id user-id}]
       (response (projects/create-project service project-templ))))

   (DELETE "/:id" [id :as request]
     (let [id (Integer. id)
           user-id (util/request-user-id request)
           project (projects/get-project service id)]
       (if (and (projects/owned-by? project user-id)
                (projects/delete-project service id))
         (response {:deleted id})
         (not-found {:error "Project not found"}))))))

(defn projects-endpoint [endpoint]
  (context "/api/projects" []
    (restrict (endpoint-routes endpoint) {:handler authenticated?})))
