(ns planwise.endpoint.providers-set
  (:require [planwise.boundary.providers-set :as providers-set]
            [planwise.boundary.jobrunner :as jobrunner]
            [compojure.core :refer :all]
            [integrant.core :as ig]
            [taoensso.timbre :as timbre]
            [ring.util.response :refer [response status not-found]]
            [planwise.util.ring :as util]
            [buddy.auth :refer [authenticated?]]
            [buddy.auth.accessrules :refer [restrict]]))

(timbre/refer-timbre)

(defn- providers-set-routes
  [{service :providers-set jobrunner :jobrunner}]
  (routes

   (GET "/" request
     (let [user-id (util/request-user-id request)
           sets    (providers-set/list-providers-set service user-id)]
       (response sets)))

   (POST "/" [name coverage-algorithm :as request]
     (let [user-id  (util/request-user-id request)
           csv-file (:tempfile (get (:multipart-params request) "file"))]
       (let [options    {:name               name
                         :owner-id           user-id
                         :coverage-algorithm coverage-algorithm}
             result     (providers-set/create-and-import-providers service options csv-file)
             provider-set-id (:id result)]
         (jobrunner/queue-job jobrunner
                              [::providers-set/preprocess-provider-set provider-set-id]
                              (providers-set/new-processing-job service provider-set-id))
         (response result))))

   (DELETE "/" [id :as request]
     (let [user-id  (util/request-user-id request)
           id       (Integer. id)]
       (try
         (providers-set/delete-referenced-provider-set service id)
         (catch Exception e
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
