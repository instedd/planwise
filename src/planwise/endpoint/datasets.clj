(ns planwise.endpoint.datasets
  (:require [compojure.core :refer :all]
            [buddy.auth.accessrules :refer [restrict]]
            [buddy.auth :refer [authenticated?]]
            [ring.util.response :refer [response]]
            [planwise.boundary.facilities :as facilities]
            [planwise.component.resmap :as resmap]))

(defn- datasets-routes
  [{:keys [facilities resmap]}]
  (routes
   (GET "/info" request
     (let [user (:identity request)
           facility-count (facilities/count-facilities facilities)
           authorised? (resmap/authorised? resmap user)]
       (response {:authorised? authorised?
                  :facility-count facility-count})))))

(defn datasets-endpoint
  [services]
  (context "/api/datasets" []
    (restrict (datasets-routes services) {:handler authenticated?})))
