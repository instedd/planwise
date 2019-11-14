(ns planwise.tasks.recompute-scenarios
  (:gen-class)
  (:require [clojure.java.io :as io]
            [duct.core :as duct]
            [integrant.core :as ig]
            [planwise.config]
            [taoensso.timbre :as timbre]
            [planwise.boundary.projects2 :as p2]))

(timbre/refer-timbre)

(duct/load-hierarchy)

(defn -main [& args]
  ;; Logging configuration for production
  (timbre/merge-config! {:level        :info
                         :ns-blacklist ["com.zaxxer.hikari.*"
                                        "org.apache.http.*"
                                        "org.eclipse.jetty.*"]})
  (let [config (-> (io/resource "planwise/cli.edn")
                   duct/read-config
                   duct/prep)
        _      (println "Initializing system")
        system (ig/init config)]
    (try
      (println (p2/list-projects (:planwise.component/projects2 system) 1))
      (finally
        (println "Shutting down system")
        (ig/halt! system)))))
