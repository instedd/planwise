(ns planwise.endpoint.routing
  (:require [planwise.boundary.routing :as routing]
            [compojure.core :refer :all]
            [integrant.core :as ig]
            [ring.util.response :refer [response status]]
            [clojure.string :refer [blank?]]
            [buddy.auth :refer [authenticated?]]
            [buddy.auth.accessrules :refer [restrict]]))

(defn invalid-query
  ([]
   (invalid-query "invalid query"))
  ([message]
   (-> (response message)
       (status 400))))

(defn coerce-algorithm [s]
  (when-not (blank? s)
    (case (.toLowerCase s)
      "alpha-shape" :alpha-shape
      "buffer"      :buffer
      :invalid)))

(defn- endpoint-routes [service]
  (routes
   (GET "/nearest-node" [lat lon]
     (if (or (empty? lat) (empty? lon))
       (invalid-query "missing parameter")
       (try
         (let [node (routing/nearest-node service (Float. lat) (Float. lon))]
           (if node
             (response {:id (:id node) :point (:point node)})
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

(defn routing-endpoint [{service :routing}]
  (context "/api/routing" []
    (restrict (endpoint-routes service) {:handler authenticated?})))

(defmethod ig/init-key :planwise.endpoint/routing
  [_ config]
  (routing-endpoint config))
