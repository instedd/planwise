(ns planwise.tasks.preprocess-facilities
  (:require [clojure.java.io :as io]
            [taoensso.timbre :as timbre]
            [duct.core :as duct]
            [integrant.core :as ig]
            [planwise.config]
            [planwise.boundary.facilities :as facilities])
  (:gen-class))

(timbre/refer-timbre)

(duct/load-hierarchy)

(defn -main [& args]
  (timbre/set-level! :warn)
  (let [system     (-> (duct/read-config (io/resource "planwise/config.edn"))
                       (duct/prep [:planwise.component/facilities])
                       (ig/init [:planwise.component/facilities]))
        facilities (:planwise.component/facilities system)]
    (try
      (when-let [cmd (first args)]
        (if (= "all" cmd)
          (do
            (println "Clearing facilities processed status to reprocess them all")
            (facilities/clear-facilities-processed-status! facilities))
          (println "Unsupported command" cmd "- ignoring")))
      (println "Pre-processing facilities")
      (let [result (facilities/preprocess-isochrones facilities)]
        (println "Processed" (count result) "facilities"))
      (finally
        (ig/halt! system)))))
