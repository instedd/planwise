(ns planwise.endpoint.datasets2
  (:require [compojure.core :refer :all]
            [integrant.core :as ig]
            [taoensso.timbre :as timbre]
            [ring.util.response :refer [response status not-found]]
            [planwise.util.ring :as util]
            [clojure.core.reducers :as r]
            [ajax.core :as ajax] ;falta declarar algo?
            [buddy.auth :refer [authenticated?]]
            [buddy.auth.accessrules :refer [restrict]]
            [planwise.model.ident :as ident]
            [planwise.component.sites-datasets :as datasets2]))

(timbre/refer-timbre)

(defn- usable-field?
  [field]
  (#{"select_one"} (:kind field)))

(defn- datasets2-routes
  [service]
  (routes

   (GET "/" request
     (let [user-id (util/request-user-id request)
           sets (datasets2/list-sites-datasets service user-id)]
       (response sets)))

   (POST "/" request
     (let [user-id (util/request-user-id request)
           importing-file (:tempfile (get (:multipart-params request) "file"))
           name           (get (:multipart-params request) "name")
           dataset-id (:id (datasets2/create-sites-dataset service name user-id))]
       (response (do
                    (datasets2/get-dataset service dataset-id)
                    (datasets2/csv-to-facilities service dataset-id importing-file)))))))


(defn datasets2-endpoint
  [{service :datasets2}]
  (context "/api/datasets2" []
    (restrict (datasets2-routes service) {:handler authenticated?})))

(defmethod ig/init-key :planwise.endpoint/datasets2
  [_ config]
  (datasets2-endpoint config))
