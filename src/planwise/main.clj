(ns planwise.main
  (:gen-class)
  (:require [clojure.java.io :as io]
            [duct.core :as duct]))

(duct/load-hierarchy)

(defn -main [& args]
  (let [keys (or (duct/parse-keys args) [:duct/daemon])]
    (-> (duct/read-config (io/resource "planwise/config.edn"))
        (duct/prep keys)
        (duct/exec keys))))

#_(timbre/refer-timbre)

#_(def prod-config
  {:api {:middleware     [[wrap-log-errors]]}
   :app {:middleware     [[wrap-log-errors]
                          [wrap-hide-errors :internal-error]]
         :internal-error (io/resource "planwise/errors/500.html")}})

#_(def config
  (meta-merge config/defaults
              config/environ
              prod-config))

#_(defn -main [& args]
  ;; Logging configuration for production
  (timbre/merge-config! {:level :info
                         :ns-blacklist ["com.zaxxer.hikari.*"
                                        "org.apache.http.*"
                                        "org.eclipse.jetty.*"]})
  (let [system (new-system config)]
    (report "Starting HTTP server on port" (-> system :http :port))
    (add-shutdown-hook ::stop-system #(component/stop system))
    (component/start system)))
