(ns planwise.main
  (:gen-class)
  (:require [clojure.java.io :as io]
            [taoensso.timbre :as timbre]
            [com.stuartsierra.component :as component]
            [duct.middleware.errors :refer [wrap-hide-errors]]
            [duct.util.runtime :refer [add-shutdown-hook]]
            [meta-merge.core :refer [meta-merge]]
            [planwise.config :as config]
            [planwise.system :refer [new-system]]))

(timbre/refer-timbre)

(def prod-config
  {:app {:middleware     [[wrap-hide-errors :internal-error]]
         :internal-error (io/resource "errors/500.html")}})

(def config
  (meta-merge config/defaults
              config/environ
              prod-config))

(defn -main [& args]
  ;; Logging configuration for development
  (timbre/merge-config! {:level :info
                         :ns-blacklist ["com.zaxxer.hikari.*"
                                        "org.apache.http.*"
                                        "org.eclipse.jetty.*"]})
  (let [system (new-system config)]
    (report "Starting HTTP server on port" (-> system :http :port))
    (add-shutdown-hook ::stop-system #(component/stop system))
    (component/start system)))
