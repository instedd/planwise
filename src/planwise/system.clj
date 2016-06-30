(ns planwise.system
  (:require [clojure.java.io :as io]
            [com.stuartsierra.component :as component]
            [duct.component.endpoint :refer [endpoint-component]]
            [duct.component.handler :refer [handler-component]]
            [duct.component.hikaricp :refer [hikaricp]]
            [duct.middleware.not-found :refer [wrap-not-found]]
            [duct.middleware.route-aliases :refer [wrap-route-aliases]]
            [meta-merge.core :refer [meta-merge]]
            [ring.component.jetty :refer [jetty-server]]
            [ring.middleware.defaults :refer [wrap-defaults site-defaults]]
            [ring.middleware.webjars :refer [wrap-webjars]]
            [planwise.component.facilities :refer [facilities-service]]
            [planwise.component.routing :refer [routing-service]]
            [planwise.endpoint.home :refer [home-endpoint]]
            [planwise.endpoint.facilities :refer [facilities-endpoint]]
            [planwise.endpoint.routing :refer [routing-endpoint]]))

(def base-config
  {:app {:middleware [[wrap-not-found :not-found]
                      [wrap-webjars]
                      [wrap-defaults :defaults]
                      [wrap-route-aliases :aliases]]
         :not-found  (io/resource "planwise/errors/404.html")
         :defaults   (meta-merge site-defaults {:static {:resources "planwise/public"}})
         :aliases    {}}})

(defn new-system [config]
  (let [config (meta-merge base-config config)]
    (-> (component/system-map
         :app                 (handler-component (:app config))
         :http                (jetty-server (:http config))
         :db                  (hikaricp (:db config))
         :facilities          (facilities-service)
         :routing             (routing-service)
         :home-endpoint       (endpoint-component home-endpoint)
         :facilities-endpoint (endpoint-component facilities-endpoint)
         :routing-endpoint    (endpoint-component routing-endpoint))
        (component/system-using
         {:http                [:app]
          :app                 [:facilities-endpoint
                                :routing-endpoint
                                :home-endpoint]
          :facilities          [:db]
          :routing             [:db]
          :facilities-endpoint [:facilities]
          :routing-endpoint    [:routing]}))))
