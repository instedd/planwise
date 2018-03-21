(ns planwise.endpoint.datasets2
  (:require [planwise.boundary.datasets2 :as datasets2]
            [planwise.component.jobrunner :as jobrunner]
            [compojure.core :refer :all]
            [integrant.core :as ig]
            [taoensso.timbre :as timbre]
            [ring.util.response :refer [response status not-found]]
            [planwise.util.ring :as util]
            [buddy.auth :refer [authenticated?]]
            [buddy.auth.accessrules :refer [restrict]]))

(timbre/refer-timbre)

(defn- datasets2-routes
  [{service :datasets2 jobrunner :jobrunner}]
  (routes

   (GET "/" request
        (let [user-id (util/request-user-id request)
              sets    (datasets2/list-datasets service user-id)]
          (response sets)))

   (POST "/" [name coverage-algorithm :as request]
         (let [user-id  (util/request-user-id request)
               csv-file (:tempfile (get (:multipart-params request) "file"))]
           (let [options    {:name               name
                             :owner-id           user-id
                             :coverage-algorithm coverage-algorithm}
                 result     (datasets2/create-and-import-sites service options csv-file)
                 dataset-id (:id result)]
             (jobrunner/queue-job jobrunner
                                  [::datasets2/preprocess-dataset dataset-id]
                                  (datasets2/new-processing-job service dataset-id))
             (response result))))))


(defn datasets2-endpoint
  [config]
  (context "/api/datasets2" []
    (restrict (datasets2-routes config) {:handler authenticated?})))

(defmethod ig/init-key :planwise.endpoint/datasets2
  [_ config]
  (datasets2-endpoint config))
