(ns planwise.endpoint.routing
  (:require [planwise.boundary.routing :as routing]
            [compojure.core :refer :all]
            [ring.util.response :refer [content-type response]]
            [clojure.data.json :as json]
            [clojure.string :refer [blank?]]))

(defn invalid-query
  ([]
   (invalid-query "invalid query"))
  ([message]
   {:status 400
    :headers {}
    :body message}))

(defn coerce-algorithm [s]
  (when-not (blank? s)
    (condp = (.toLowerCase s)
      "alpha-shape" :alpha-shape
      "buffer" :buffer
      :invalid)))

(defn routing-endpoint [{service :routing}]
  (context "/routing" []
    (GET "/" [] "routing endpoint")

    (GET "/nearest-node" [lat lon]
      (if (or (empty? lat) (empty? lon))
        (invalid-query "missing parameter")
        (try
          (let [node (routing/nearest-node service (Float. lat) (Float. lon))]
           (if node
             (response (json/write-str {:id (:id node)
                                        :point (:point node)}))
             (response nil)))
          (catch NumberFormatException e
            (invalid-query (str "invalid parameter: " (.getMessage e)))))))

    (GET "/isochrone" [node-id threshold algorithm]
      (let [algorithm (coerce-algorithm algorithm)]
        (if (or (empty? node-id) (empty? threshold) (= :invalid algorithm))
          (invalid-query "missing parameter or invalid algorithm")
          (try
            (let [node-id (Integer. node-id)
                  distance (Float. threshold)
                  polygon (routing/compute-isochrone service node-id distance algorithm)]
              (response polygon))
            (catch NumberFormatException e
              (invalid-query (str "invalid parameter: " (.getMessage e))))))))))
