(ns planwise.endpoint.population
  (:require [compojure.core :refer :all]
            [ring.util.response :refer [response]]
            [buddy.auth :refer [authenticated?]]
            [buddy.auth.accessrules :refer [restrict]]
            [planwise.component.population :as population]
            [integrant.core :as ig]))

(defn- population-routes
  [service]
  (routes
    (GET "/" [request]
      (response (population/list-population-sources service)))))

(defn population-endpoint
  [{service :population}]
  (context "/api/population" []
    (restrict (population-routes service) {:handler authenticated?})))

(defmethod ig/init-key :planwise.endpoint/population
  [_ config]
  (population-endpoint config))
