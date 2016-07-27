(ns planwise.config
  (:require [environ.core :refer [env]]))

(def defaults
  ;; ^:displace
  {:http {:port 3000}
   :auth {:guisso-url "https://login.instedd.org"}
   :resmap {:url "http://resourcemap.instedd.org"}
   :maps {:demo-tile-url "http://planwise-maps-stg.instedd.org/mapcache/gmaps/kenya@GoogleMapsCompatible/{z}/{x}/{y}.png"}})

(def environ
  {:http   {:port                 (some-> env :port Integer.)}
   :db     {:uri                  (env :database-url)}
   :auth   {; Base Guisso URL
            :guisso-url           (env :guisso-url)
            ; Guisso credentials for OAuth2 authentication
            :guisso-client-id     (env :guisso-client-id)
            :guisso-client-secret (env :guisso-client-secret)}
   :resmap {:url                  (env :resourcemap-url)}
   :maps   {:demo-tile-url        (env :demo-tile-url)}})
