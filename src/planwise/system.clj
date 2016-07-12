(ns planwise.system
  (:require [clojure.java.io :as io]
            [taoensso.timbre :as timbre]
            [com.stuartsierra.component :as component]
            [duct.component.endpoint :refer [endpoint-component]]
            [duct.component.handler :refer [handler-component]]
            [duct.component.hikaricp :refer [hikaricp]]
            [duct.middleware.not-found :refer [wrap-not-found]]
            [duct.middleware.route-aliases :refer [wrap-route-aliases]]
            [meta-merge.core :refer [meta-merge]]
            [ring.component.jetty :refer [jetty-server]]
            [ring.middleware.defaults :refer [wrap-defaults api-defaults site-defaults]]
            [ring.middleware.json :refer [wrap-json-response wrap-json-params]]
            [ring.middleware.webjars :refer [wrap-webjars]]
            [ring.middleware.session.cookie :refer [cookie-store]]

            [buddy.auth :refer [authenticated? throw-unauthorized]]
            [buddy.auth.backends.session :refer [session-backend]]
            [buddy.auth.middleware :refer [wrap-authentication wrap-authorization]]

            [planwise.component.compound-handler :refer [compound-handler-component]]
            [planwise.component.auth :refer [auth-service]]
            [planwise.component.facilities :refer [facilities-service]]
            [planwise.component.routing :refer [routing-service]]
            [planwise.component.projects :refer [projects-service]]
            [planwise.endpoint.home :refer [home-endpoint]]
            [planwise.endpoint.auth :refer [auth-endpoint]]
            [planwise.endpoint.facilities :refer [facilities-endpoint]]
            [planwise.endpoint.projects :refer [projects-endpoint]]
            [planwise.endpoint.routing :refer [routing-endpoint]]))

(timbre/refer-timbre)

(def base-config
  {:auth {:openid-identifier "https://login.instedd.org/openid"}
   :api {:middleware [[wrap-json-params]
                      [wrap-json-response]
                      [wrap-defaults :defaults]
                      [wrap-route-aliases :aliases]]
         :defaults   (meta-merge api-defaults {})
         :aliases    {}}
   :app {:middleware [[wrap-not-found :not-found]
                      [wrap-webjars]
                      [wrap-defaults :defaults]
                      [wrap-route-aliases :aliases]]
         :not-found  (io/resource "planwise/errors/404.html")
         :defaults   (meta-merge site-defaults
                                 {:static {:resources "planwise/public"}
                                  :session {:store (cookie-store)
                                            :cookie-attrs {:max-age (* 24 3600)}
                                            :cookie-name "planwise-session"}})
         :aliases    {}}

   :webapp {:handlers         ; Vector order matters, api handler is evaluated first
            [:api :app]}})

(defn new-system [config]
  (let [config (meta-merge base-config config)]
    (-> (component/system-map
         :app                 (handler-component (:app config))
         :api                 (handler-component (:api config))
         :webapp              (compound-handler-component (:webapp config))
         :http                (jetty-server (:http config))
         :db                  (hikaricp (:db config))
         :auth                (auth-service (:auth config))
         :facilities          (facilities-service)
         :projects            (projects-service)
         :routing             (routing-service)
         :auth-endpoint       (endpoint-component auth-endpoint)
         :home-endpoint       (endpoint-component home-endpoint)
         :facilities-endpoint (endpoint-component facilities-endpoint)
         :projects-endpoint   (endpoint-component projects-endpoint)
         :routing-endpoint    (endpoint-component routing-endpoint))
        (component/system-using
         {:http                {:app :webapp}
          :webapp              [:app :api]
          :api                 [:facilities-endpoint
                                :projects-endpoint
                                :routing-endpoint]
          :app                 [:home-endpoint
                                :auth-endpoint]
          :facilities          [:db]
          :projects            [:db]
          :routing             [:db]
          :auth-endpoint       [:auth]
          :facilities-endpoint [:facilities]
          :projects-endpoint   [:projects]
          :routing-endpoint    [:routing]}))))
