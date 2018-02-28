(ns planwise.endpoint.monitor
  (:require [compojure.core :refer :all]
            [integrant.core :as ig]
            [ring.util.response :refer [response]]
            [buddy.auth :refer [authenticated?]]
            [buddy.auth.accessrules :refer [restrict]]
            [planwise.util.ring :as util]))

(defn whoami-handler [request]
  (let [email (util/request-user-email request)
        user-id (util/request-user-id request)]
    (response {:id user-id :email email})))

(defn monitor-endpoint [system]
  (context "/api" []
    (GET "/ping" [] "pong")
    (GET "/whoami" req
      (restrict whoami-handler {:handler authenticated?}))))

(defmethod ig/init-key :planwise.endpoint/monitor
  [_ config]
  (monitor-endpoint config))
