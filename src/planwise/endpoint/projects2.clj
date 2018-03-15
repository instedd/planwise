(ns planwise.endpoint.projects2
  (:require [compojure.core :refer :all]
            [integrant.core :as ig]
            [taoensso.timbre :as timbre]
            [ring.util.response :refer [response status not-found]]
            [planwise.util.ring :as util]
            [clojure.core.reducers :as r]
            [buddy.auth :refer [authenticated?]]
            [buddy.auth.accessrules :refer [restrict]]
            [planwise.model.ident :as ident]
            [planwise.component.projects2 :as projects2]))

(timbre/refer-timbre)

(defn- projects2-routes
  [service]
  (routes
   (POST "/" request
     (let [user-id    (util/request-user-id request)
           project-id (projects2/create-project service user-id)]
      (response project-id)))

   (PUT "/:id" [id name :as request]
     (let [user-id    (util/request-user-id request)
           project-id (Integer. id)]
        (projects2/update-project service project-id name)))

   (GET "/:id" [id :as request]
     (let [user-id (util/request-user-id request)
           project (first (projects2/get-project service (Integer. id)))]
       (if (nil? project)
           (not-found project)
           (response project))))))


(defn projects2-endpoint
  [{service :projects2}]
  (context "/api/projects2" []
    (restrict (projects2-routes service) {:handler authenticated?})))

(defmethod ig/init-key :planwise.endpoint/projects2
  [_ config]
  (projects2-endpoint config))
