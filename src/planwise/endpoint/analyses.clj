(ns planwise.endpoint.analyses
  (:require [compojure.core :refer :all]
            [ring.util.response :refer [content-type response not-found]]
            [clojure.data.json :as json]
            [clojure.string :as str]
            [buddy.auth :refer [authenticated?]]
            [buddy.auth.accessrules :refer [restrict]]
            [planwise.component.analyses :as analyses]
            [planwise.util.ring :as util]))

(defn- endpoint-routes [service]
  (routes

   (GET "/" request
        (let [user-id (util/request-user-id request)
              analyses (analyses/select-analyses-for-user service user-id)]
          (response analyses)))

   (POST "/" request
         (let [user-id (util/request-user-id request)
               result (analyses/create-analysis-for-user! service user-id)
               analysis-id (:id result)
               analysis (analyses/find-analysis service analysis-id)]
           (response analysis)))))

(defn analyses-endpoint [{store :analyses-store}]
  (context "/api/analyses" []
    (restrict (endpoint-routes store) {:handler authenticated?})))
