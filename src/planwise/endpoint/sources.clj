(ns planwise.endpoint.sources
  (:require [compojure.core :refer :all]
            [ring.util.response :refer [response]]
            [buddy.auth :refer [authenticated?]]
            [buddy.auth.accessrules :refer [restrict]]
            [planwise.component.sources :as sources]
            [integrant.core :as ig]))

(defn- sources-routes
  [service]
  (routes
   (GET "/" [request]
     (response (sources/list-sources service)))))

(defn sources-endpoint
  [{service :sources}]
  (context "/api/sources" []
    (restrict (sources-routes service) {:handler authenticated?})))

(defmethod ig/init-key :planwise.endpoint/sources
  [_ config]
  (sources-endpoint config))
