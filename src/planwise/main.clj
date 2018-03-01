(ns planwise.main
  (:gen-class)
  (:require [clojure.java.io :as io]
            [duct.core :as duct]
            [taoensso.timbre :as timbre]))

(timbre/refer-timbre)

(duct/load-hierarchy)

(defn -main [& args]
  ;; Logging configuration for production
  (timbre/merge-config! {:level :info
                         :ns-blacklist ["com.zaxxer.hikari.*"
                                        "org.apache.http.*"
                                        "org.eclipse.jetty.*"]})
  (let [keys (or (duct/parse-keys args) [:duct/daemon])]
    (-> (duct/read-config (io/resource "planwise/config.edn"))
        (duct/prep keys)
        (duct/exec keys))))

