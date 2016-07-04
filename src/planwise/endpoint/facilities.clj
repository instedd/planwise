(ns planwise.endpoint.facilities
  (:require [planwise.boundary.facilities :as facilities]
            [compojure.core :refer :all]
            [ring.util.response :refer [content-type response]]
            [clojure.data.json :as json]))

(defn facilities-endpoint [{service :facilities}]
  (context "/facilities" []
    (GET "/" [] (let [facilities (facilities/list-facilities service)]
                  (-> (response (json/write-str facilities))
                      (content-type "application/json"))))

    (GET "/with-isochrones" [threshold algorithm simplify]
      (let [facilities (facilities/list-with-isochrones service
                                                        (Integer. threshold)
                                                        algorithm
                                                        (Float. simplify))]
        (-> (response (json/write-str facilities))
            (content-type "application/json"))))

    (GET "/isochrone" []
      (let [isochrone (facilities/isochrone-all-facilities service 5400)]
        (-> (response (json/write-str isochrone))
            (content-type "application/json"))))))
