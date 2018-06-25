(ns planwise.endpoint.sources
  (:require [compojure.core :refer :all]
            [ring.util.response :refer [response]]
            [buddy.auth :refer [authenticated?]]
            [buddy.auth.accessrules :refer [restrict]]
            [planwise.component.sources :as sources]
            [planwise.util.ring :as util]
            [integrant.core :as ig]))

(defn- sources-routes
  [service]
  (routes
   (GET "/" request
     (let [user-id (util/request-user-id request)]
       (response (sources/list-sources service user-id))))

   (POST "/" [name :as request]
     (let [csv-file (:tempfile (get (:multipart-params request) "csvfile"))]
       (response (sources/import-from-csv service {:name name} csv-file))))))

(defn sources-endpoint
  [{service :sources}]
  (context "/api/sources" []
    (restrict (sources-routes service) {:handler authenticated?})))

(defmethod ig/init-key :planwise.endpoint/sources
  [_ config]
  (sources-endpoint config))