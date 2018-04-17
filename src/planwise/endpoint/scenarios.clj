(ns planwise.endpoint.scenarios
  (:require [compojure.core :refer :all]
            [integrant.core :as ig]
            [taoensso.timbre :as timbre]
            [clojure.spec.alpha :as s]
            [ring.util.response :refer [response status not-found]]
            [planwise.util.ring :as util]
            [buddy.auth :refer [authenticated?]]
            [buddy.auth.accessrules :refer [restrict]]
            [clojure.core.reducers :as r]
            [planwise.boundary.scenarios :as scenarios]
            [planwise.boundary.projects2 :as projects2]))

(timbre/refer-timbre)

(defn- scenarios-routes
  [{service :scenarios projects2 :projects2}]
  [service]
  (routes
   (GET "/:id" [id as request]
     (let [scenario (scenarios/get-scenario service (Integer. id))]
       (if (nil? (:id scenario)) (not-found) (response (dissoc scenario :updated-at)))))

   (PUT "/:id" [id scenario as request]
     (let [id (Integer. id)
           scenario   (assoc scenario :id id)
           project-id (:project-id (scenarios/get-scenario service id))
           project    (projects2/get-project projects2 project-id)]
       (scenarios/update-scenario service project scenario)
       (response (dissoc (scenarios/get-scenario service id) scenario :updated-at))))

   (POST "/:id/copy" [id as request]
     (let [id         (Integer. id)
           scenario   (scenarios/get-scenario service id)
           project-id (:project-id scenario)
           project    (projects2/get-project projects2 project-id)
           {:keys [name changeset]} scenario
           next-name  (scenarios/next-scenario-name service project-id name)]
       (response (scenarios/create-scenario service project {:name next-name
                                                             :changeset changeset}))))))

(defn scenarios-endpoint
  [config]
  (context "/api/scenarios" []
    (restrict (scenarios-routes config) {:handler authenticated?})))

(defmethod ig/init-key :planwise.endpoint/scenarios
  [_ config]
  (scenarios-endpoint config))
