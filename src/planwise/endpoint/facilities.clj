(ns planwise.endpoint.facilities
  (:require [planwise.boundary.facilities :as facilities]
            [compojure.core :refer :all]
            [buddy.auth.accessrules :refer [restrict]]
            [buddy.auth :refer [authenticated?]]
            [ring.util.response :refer [response]]))

(defn facilities-criteria [type region]
  {:region (if region (Integer. region) nil)
   :types (if type (vals type) nil)})

(defn- endpoint-routes [service]
  (routes

   (GET "/" [type region]
      (let [facilities (facilities/list-facilities service (facilities-criteria type region))]
        (response {:count (count facilities)
                   :facilities facilities})))

   (GET "/with-isochrones" [threshold algorithm simplify type region]
     (let [criteria (facilities-criteria type region)
           isochrone {:threshold (Integer. threshold)
                      :algorithm algorithm
                      :simplify (if simplify (Float. simplify) nil)}
           facilities (facilities/list-with-isochrones service isochrone criteria)]
       (response facilities)))

   (GET "/isochrone" [threshold]
     (let [threshold (Integer. (or threshold 5400))
           isochrone (facilities/isochrone-all-facilities service threshold)]
       (response isochrone)))))

(defn facilities-endpoint [{service :facilities}]
  (context "/api/facilities" []
    (restrict (endpoint-routes service) {:handler authenticated?})))
