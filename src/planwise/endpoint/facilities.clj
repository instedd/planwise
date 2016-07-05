(ns planwise.endpoint.facilities
  (:require [planwise.boundary.facilities :as facilities]
            [compojure.core :refer :all]
            [ring.util.response :refer [response]]))

(defn facilities-endpoint [{service :facilities}]
  (context "/facilities" []
    (GET "/" [] (let [facilities (facilities/list-facilities service)]
                  (response facilities)))

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
