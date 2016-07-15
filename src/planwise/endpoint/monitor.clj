(ns planwise.endpoint.monitor
  (:require [compojure.core :refer :all]
            [ring.util.response :refer [response]]
            [buddy.auth :refer [authenticated?]]
            [buddy.auth.accessrules :refer [restrict]]))

(defn whoami-handler [request]
  (let [id (-> request :identity :user)]
    (response {:email id})))

(defn monitor-endpoint [system]
  (context "/api" []
    (GET "/ping" [] "pong")
    (GET "/whoami" req
      (restrict whoami-handler {:handler authenticated?}))))
