(ns planwise.endpoint.facilities
  (:require [planwise.boundary.facilities :as facilities]
            [compojure.core :refer :all]
            [ring.util.response :refer [response]]))

(defn facilities-endpoint [{service :facilities}]
  (context "/api/facilities" []
    (GET "/" {{types :type} :params} (let [facilities (facilities/list-facilities-with-types service (vals types))]
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
