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
           authorised? (resmap/authorised? resmap user)
           collections (when authorised?
                         (resmap/list-user-collections resmap user))]
       (response {:authorised? authorised?
                  :facility-count facility-count
                  :collections collections})))
   (GET "/collection-info/:coll-id" [coll-id :as request]
     (let [user (:identity request)
           fields (resmap/list-collection-fields resmap user coll-id)]
       (response {:fields fields})))))

(defn datasets-endpoint
  [services]
  (context "/api/datasets" []
    (restrict (datasets-routes services) {:handler authenticated?})))
