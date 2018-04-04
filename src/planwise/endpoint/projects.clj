(ns planwise.endpoint.projects
  (:require [compojure.core :refer :all]
            [integrant.core :as ig]
            [ring.util.response :refer [content-type response status not-found]]
            [clojure.data.json :as json]
            [planwise.boundary.maps :as maps]
            [buddy.auth :refer [authenticated?]]
            [buddy.auth.accessrules :refer [restrict]]
            [planwise.util.ring :as util]
            [planwise.boundary.projects :as projects]
            [planwise.boundary.facilities :as facilities]
            [planwise.model.projects :refer [owned-by?]]
            [clojure.walk :refer [keywordize-keys]]
            [clojure.string :as str]
            [clojure.set :refer [rename-keys]]))

(defn- assoc-extra-data
  [with {:keys [dataset-id region-id], :as project} {:keys [facilities maps projects]}]
  (let [criteria {:region region-id
                  :types (or (get-in project [:filters :facilities :type]) [])}

        with-extra-data (letfn [(project-facilities []
                                  (facilities/list-facilities facilities dataset-id criteria))
                                (demand []
                                        (when-let [time (get-in project [:filters :transport :time])]
                                          (let [polygons (facilities/polygons-in-region facilities dataset-id {:threshold time} criteria)]
                                            (some->> polygons
                                                     (seq)
                                                     (map #(rename-keys % {:facility-polygon-id :polygon-id
                                                                           :facility-population :population
                                                                           :facility-region-population :population-in-region}))
                                                     (maps/demand-map maps (:region-id project))))))]
                          (case with
                            :facilities
                            (assoc project :facilities (project-facilities))

                            :demand
                            (merge project (demand))

                            :facilities-with-demand
                            (-> project
                                (merge (demand))
                                (assoc :facilities (project-facilities)))

                            project))]

    (if (:read-only project)
      with-extra-data
      (assoc with-extra-data :shares (projects/list-project-shares projects (:id project))))))

(defn- endpoint-routes
  [{service :projects facilities :facilities, :as services}]
  (routes
   (GET "/" request
     (let [user-id (util/request-user-id request)
           projects (projects/list-projects-for-user service user-id)]
       (response projects)))

   (POST "/:id/access/:token" [id token with :as request]
     (let [user-id (util/request-user-id request)
           project (projects/create-project-share service (Integer. id) token user-id)]
       (if project
         (response (assoc-extra-data (keyword with) project services))
         (not-found {:error "Project not found or invalid token"}))))

   (POST "/:id/token/reset" [id :as request]
     (let [user-id (util/request-user-id request)
           project-id (Integer. id)
           project (projects/get-project service project-id)]
       (if (owned-by? project user-id)
         (if-let [token (projects/reset-share-token service project-id)]
           (response {:token token})
           (-> (response {:status "failure"})
               (status 400)))
         (not-found {:error "Project not found"}))))

   (POST "/:id/share" [id emails :as request]
     (let [user-id (util/request-user-id request)
           project-id (Integer. id)
           project (projects/get-project service project-id)
           host (util/absolute-url "/" request)]
       (if (owned-by? project user-id)
         (if (projects/share-via-email service project emails {:host host})
           (response {:emails emails})
           (-> (response {:status "failure"})
               (status 400)))
         (not-found {:error "Project not found"}))))

   (GET "/:id" [id with :as request]
     (let [user-id (util/request-user-id request)
           project (projects/get-project service (Integer. id) user-id)]
       (if project
         (response (assoc-extra-data (keyword with) project services))
         (not-found {:error "Project not found"}))))

   (PUT "/:id" [id filters with state :as request]
     (let [id (Integer. id)
           filters (keywordize-keys filters)
           user-id (util/request-user-id request)
           project (projects/get-project service id)]
       (if (owned-by? project user-id)
         (if-let [project (projects/update-project service {:id id
                                                            :filters filters
                                                            :state state})]
           (response (assoc-extra-data (keyword with) project services))
           (-> (response {:status "failure"})
               (status 400)))
         (not-found {:error "Project not found"}))))

   (POST "/" [goal dataset-id region-id :as request]
     (let [goal (str/trim goal)
           dataset-id (Integer. dataset-id)
           region-id (Integer. region-id)
           user-id (util/request-user-id request)
           project-templ {:goal goal
                          :dataset-id dataset-id
                          :region-id region-id
                          :owner-id user-id}]
       (response (projects/create-project service project-templ))))

   (DELETE "/:id" [id :as request]
     (let [id (Integer. id)
           user-id (util/request-user-id request)
           project (projects/get-project service id)]
       (if (and (owned-by? project user-id)
                (projects/delete-project service id))
         (response {:deleted id})
         (not-found {:error "Project not found"}))))

   (DELETE "/:id/access" [id :as request]
     (let [project-id (Integer. id)
           user-id (util/request-user-id request)]
       (if (projects/delete-project-share service project-id user-id)
         (response {:project-id project-id})
         (not-found {:error "Project share not found"}))))

   (DELETE "/:id/shares/:user" [id user :as request]
     (let [project-id (Integer. id)
           share-user-id (Integer. user)
           owner-id (util/request-user-id request)
           project (projects/get-project service project-id)]
       (if (and (owned-by? project owner-id)
                (projects/delete-project-share service project-id share-user-id))
         (response {:project-id project-id, :user-id share-user-id})
         (not-found {:error "Project share not found"}))))))

(defn projects-endpoint [endpoint]
  (context "/api/projects" []
    (restrict (endpoint-routes endpoint) {:handler authenticated?})))

(defmethod ig/init-key :planwise.endpoint/projects
  [_ config]
  (projects-endpoint config))
