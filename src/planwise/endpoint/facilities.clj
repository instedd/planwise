(ns planwise.endpoint.facilities
  (:require [compojure.core :refer :all]
            [ring.util.response :refer [content-type response]]
            [clojure.data.json :as json]
            [planwise.facilities.core :as facilities]))

(defn facilities-endpoint [{{db :spec} :db}]
  (context "/facilities" []
    (GET "/" [] (let [facilities (facilities/get-facilities db)]
                  (-> (response (json/write-str facilities))
                      (content-type "application/json"))))

    (GET "/with-isochrones" [threshold algorithm simplify]
      (let [facilities (facilities/get-with-isochrones db (Integer. threshold) algorithm (Float. simplify))]
        (-> (response (json/write-str facilities))
            (content-type "application/json"))))

    (GET "/isochrone" []
      (let [isochrone (facilities/get-isochrone-facilities db 5400)]
        (-> (response (json/write-str isochrone))
            (content-type "application/json"))))))
