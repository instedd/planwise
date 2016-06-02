(ns planwise.endpoint.facilities
  (:require [compojure.core :refer :all]
            [ring.util.response :refer [content-type response]]
            [clojure.data.json :as json]
            [planwise.facilities.core :as facilities]))

(defn facilities-endpoint [{{db :spec} :db}]
  (context "/facilities" []
    (GET "/" [] (let [facilities (facilities/get-facilities db)]
                  (-> (response (json/write-str facilities))
                      (content-type "application/json"))))))
