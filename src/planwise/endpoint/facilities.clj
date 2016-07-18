(ns planwise.endpoint.facilities
  (:require [planwise.boundary.facilities :as facilities]
            [compojure.core :refer :all]
            [buddy.auth.accessrules :refer [restrict]]
            [buddy.auth :refer [authenticated?]]
            [ring.util.response :refer [response]]))

(defn- endpoint-routes [service]
  (routes
   (GET "/" [type :as req] (let [facilities (facilities/list-facilities-with-types service (vals type))]
                             (println req)
                                      (println type)
                                      (response {:count (count facilities) :facilities facilities})))

   (GET "/with-isochrones" [threshold algorithm simplify]
     (let [facilities (facilities/list-with-isochrones service
                                                       (Integer. threshold)
                                                       algorithm
                                                       (Float. simplify))]
       (response facilities)))

   (GET "/isochrone" [threshold]
     (let [threshold (Integer. (or threshold 5400))
           isochrone (facilities/isochrone-all-facilities service threshold)]
       (response isochrone)))))

(defn facilities-endpoint [{service :facilities}]
  (context "/api/facilities" []
    (restrict (endpoint-routes service) {:handler authenticated?})))
