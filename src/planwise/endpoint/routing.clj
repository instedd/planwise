(ns planwise.endpoint.routing
  (:require [planwise.boundary.routing :as routing]
            [compojure.core :refer :all]
            [ring.util.response :refer [content-type response]]
            [clojure.data.json :as json]))

(def invalid-query
  {:status 400
   :headers {}
   :body "invalid query"})

(defn coerce-algorithm [s]
  (when (some? s)
    (condp = (.toLowerCase s)
      "alpha-shape" :alpha-shape
      "buffer" :buffer
      :invalid)))

(defn routing-endpoint [{service :routing}]
  (context "/routing" []
    (GET "/" [] "routing endpoint")

    (GET "/nearest-node" [lat lon]
      (if (or (empty? lat) (empty? lon))
        invalid-query
        (let [node (routing/nearest-node service (Float. lat) (Float. lon))]
          (if node
            (response (json/write-str {:id (:id node)
                                       :point (:point node)}))
            (response nil)))))

    (GET "/isochrone" [node-id threshold algorithm]
      (let [algorithm (coerce-algorithm algorithm)]
        (if (or (empty? node-id) (empty? threshold) (= :invalid algorithm))
          invalid-query
          (let [node-id (Integer. node-id)
                distance (Float. threshold)
                polygon (routing/compute-isochrone service node-id distance algorithm)]
            (response polygon)))))))
