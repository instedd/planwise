(ns planwise.config
  (:require [environ.core :refer [env]]))

(def defaults
  ;; ^:displace
  {:http       {:port                3000}
   :auth       {:guisso-url          "https://login.instedd.org"}
   :resmap     {:url                 "http://resourcemap.instedd.org"}
   :paths      {:bin                 "bin/"
                :scripts             "scripts/"
                :data                "data/"}
   :maps       {:mapserver-url       "http://localhost:5002/mapcache?"
                :facilities-capacity 100000
                :calculate-demand    true}
   :facilities {:raster-isochrones true}})

(def environ
  {:http       {:port                 (some-> env :port Integer.)}
   :db         {:uri                  (env :database-url)}
   :auth       {; Base Guisso URL
                :guisso-url           (env :guisso-url)
                ; Guisso credentials for OAuth2 authentication
                :guisso-client-id     (env :guisso-client-id)
                :guisso-client-secret (env :guisso-client-secret)}
   :resmap     {:url                  (env :resourcemap-url)}
   :paths      {:bin                  (env :bin-path)
                :scripts              (env :scripts-path)
                :data                 (env :data-path)}
   :maps       {:mapserver-url        (env :mapserver-url)
                :facilities-capacity  (some-> env :maps-facilities-capacity Integer.)
                :calculate-demand     (some-> env :calculate-demand (Boolean/valueOf))}
   :facilities {:raster-isochrones    (some-> env :raster-isochrones (Boolean/valueOf))}})
