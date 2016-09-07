(ns planwise.tasks.preprocess-facilities
  (:require [clojure.java.io :as io]
            [clojure.java.jdbc :as jdbc]
            [resauce.core :as resauce]
            [taoensso.timbre :as timbre]
            [com.stuartsierra.component :as component]
            [duct.component.hikaricp :refer [hikaricp]]
            [meta-merge.core :refer [meta-merge]]
            [planwise.config :as config]
            [planwise.component.facilities :as facilities :refer [facilities-service]]
            [planwise.component.runner :refer [runner-service]])
  (:gen-class))

(timbre/refer-timbre)

(def config
  (meta-merge config/defaults
              config/environ))

(defn new-system [config]
  (-> (component/system-map
       :db         (hikaricp (:db config))
       :runner     (runner-service (:paths config))
       :facilities (facilities-service (:facilities config)))
      (component/system-using
        {:facilities          [:db :runner]})))

(defn -main [& args]
  (timbre/set-level! :warn)
  (let [system (new-system config)
        system (component/start system)
        facilities (:facilities system)]
    (when-let [cmd (first args)]
      (if (= "all" cmd)
        (do
          (println "Clearing facilities processed status to reprocess them all")
          (facilities/clear-facilities-processed-status! facilities))
        (println "Unsupported command" cmd "- ignoring")))

    (println "Pre-processing facilities")
    (let [result (facilities/preprocess-isochrones facilities)]
      (println "Processed" (count result) "facilities"))))
