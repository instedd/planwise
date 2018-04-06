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
            [planwise.component.scenarios :as scenarios]))

(timbre/refer-timbre)

(defn- scenarios-routes
  [service]
  (routes
   (GET "/:id" [id as request]
     (let [user-id          (util/request-user-id request)
           scenario         (scenarios/get-scenario service (Integer. id))]
       (response scenario)))))

(defn scenarios-endpoint
  [{service :scenarios}]
  (context "/api/scenarios" []
    (restrict (scenarios-routes service) {:handler authenticated?})))

(defmethod ig/init-key :planwise.endpoint/scenarios
  [_ config]
  (scenarios-endpoint config))
