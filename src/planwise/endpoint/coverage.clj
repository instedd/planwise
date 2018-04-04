(ns planwise.endpoint.coverage
  (:require [planwise.boundary.coverage :as coverage]
            [compojure.core :refer :all]
            [ring.util.response :refer [response]]
            [buddy.auth :refer [authenticated?]]
            [buddy.auth.accessrules :refer [restrict]]
            [integrant.core :as ig]))

(defn endpoint-routes
  [service]
  (routes
   (GET "/algorithms" req
     (response (coverage/supported-algorithms service)))))

(defmethod ig/init-key :planwise.endpoint/coverage
  [_ {:keys [coverage]}]
  (context "/api/coverage" []
    (restrict (endpoint-routes coverage) {:handler authenticated?})))
