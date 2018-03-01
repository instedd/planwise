(ns planwise.tasks.preprocess-facilities
  (:require [clojure.java.io :as io]
            [clojure.java.jdbc :as jdbc]
            [resauce.core :as resauce]
            [taoensso.timbre :as timbre]
            [planwise.boundary.facilities :as facilities]
            #_[planwise.component.facilities :as facilities :refer [facilities-service]]
            #_[planwise.component.runner :refer [runner-service]])
  (:gen-class))

(timbre/refer-timbre)

#_(defn new-system [config]
  (-> (component/system-map
       :db         nil #_(hikaricp (:db config))
       :runner     nil #_(runner-service (:paths config))
       :facilities nil #_(facilities-service (:facilities config)))
      (component/system-using
        {:facilities          [:db :runner]})))

(defn -main [& args]
  (timbre/set-level! :warn)
  #_(let [system (new-system config)
        system (component/start system)
        facilities (:facilities system)]
    (when-let [cmd (first args)]
      (if (= "all" cmd)
        (do
          (println "Clearing facilities processed status to reprocess them all")
          #_(facilities/clear-facilities-processed-status! facilities))
        (println "Unsupported command" cmd "- ignoring")))

    (println "Pre-processing facilities")
    (let [result nil #_(facilities/preprocess-isochrones facilities)]
      (println "Processed" (count result) "facilities"))))
