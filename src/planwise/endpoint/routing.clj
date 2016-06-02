(ns planwise.endpoint.routing
  (:require [compojure.core :refer :all]
            [ring.util.response :refer [content-type response]]
            [clojure.data.json :as json]
            [planwise.routing.core :as routing]))

(def invalid-query
  {:status 400
   :headers {}
   :body "invalid query"})

(defn routing-endpoint [{{db :spec} :db}]
  (context "/routing" []
    (GET "/" [] "routing endpoint")

    (GET "/nearest-node" [lat lon]
      (if (or (empty? lat) (empty? lon))
        invalid-query
        (let [node (routing/nearest-node db (Float. lat) (Float. lon))]
          (if node
            (response (json/write-str {:id (:id node)
                                       :point (:point node)}))
            (response nil)))))

    (GET "/isochrone" [node-id threshold]
      (if (or (empty? node-id) (empty? threshold))
        invalid-query
        (let [polygon (routing/isochrone db (Integer. node-id) (Float. threshold))]
          (response polygon))))))
