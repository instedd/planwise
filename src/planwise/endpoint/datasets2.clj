(ns planwise.endpoint.datasets2
  (:require [planwise.boundary.datasets2 :as datasets2]
            [compojure.core :refer :all]
            [integrant.core :as ig]
            [taoensso.timbre :as timbre]
            [ring.util.response :refer [response status not-found]]
            [planwise.util.ring :as util]
            [buddy.auth :refer [authenticated?]]
            [buddy.auth.accessrules :refer [restrict]]))

(timbre/refer-timbre)

(defn- datasets2-routes
  [service]
  (routes

   (GET "/" request
     (let [user-id (util/request-user-id request)
           sets (datasets2/list-datasets service user-id)]
       (response sets)))

   (POST "/" [name coverage-algorithm :as request]
     (let [user-id  (util/request-user-id request)
           csv-file (:tempfile (get (:multipart-params request) "file"))
           dataset-id (:id (datasets2/create-dataset service name user-id))]
       (datasets2/import-csv service dataset-id csv-file)
       (response (datasets2/get-dataset service dataset-id))))))


(defn datasets2-endpoint
  [{service :datasets2}]
  (context "/api/datasets2" []
    (restrict (datasets2-routes service) {:handler authenticated?})))

(defmethod ig/init-key :planwise.endpoint/datasets2
  [_ config]
  (datasets2-endpoint config))
