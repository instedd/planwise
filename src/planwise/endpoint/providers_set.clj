(ns planwise.endpoint.providers-set
  (:require [planwise.boundary.providers-set :as providers-set]
            [compojure.core :refer :all]
            [integrant.core :as ig]
            [taoensso.timbre :as timbre]
            [ring.util.response :refer [response status not-found]]
            [planwise.util.ring :as util]
            [buddy.auth :refer [authenticated?]]
            [buddy.auth.accessrules :refer [restrict]]))

(timbre/refer-timbre)

(defn- providers-set-routes
  [{service :providers-set}]
  (routes

   (GET "/" request
     (let [user-id (util/request-user-id request)
           sets    (providers-set/list-providers-set service user-id)]
       (response sets)))

   (POST "/" [name :as request]
     (let [user-id  (util/request-user-id request)
           csv-file (:tempfile (get (:multipart-params request) "file"))]
       (let [options    {:name               name
                         :owner-id           user-id}
             result     (providers-set/create-and-import-providers service options csv-file)
             provider-set-id (:id result)]
         (response result))))

   (DELETE "/" [id :as request]
     (let [user-id  (util/request-user-id request)
           id       (Integer. id)]
       (try
         (providers-set/delete-provider-set service id)
         {:status 204}
         (catch Exception e
           (error e "Failed to delete provider set")
           {:status  400
            :headers {}
            :body    (ex-data e)}))))))


(defn providers-set-endpoint
  [config]
  (context "/api/providers" []
    (restrict (providers-set-routes config) {:handler authenticated?})))

(defmethod ig/init-key :planwise.endpoint/providers-set
  [_ config]
  (providers-set-endpoint config))
