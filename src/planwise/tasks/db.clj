(ns planwise.tasks.db
  (:require [clojure.java.io :as io]
            [clojure.java.jdbc :as jdbc]
            [resauce.core :as resauce]
            [taoensso.timbre :as timbre]
            [com.stuartsierra.component :as component]
            [duct.component.ragtime :refer [ragtime migrate rollback]]
            [duct.component.hikaricp :refer [hikaricp]]
            [meta-merge.core :refer [meta-merge]]
            [planwise.config :as config])
  (:gen-class))

(timbre/refer-timbre)

(def config
  (meta-merge config/defaults
              config/environ))

(defn new-system [config]
  (-> (component/system-map
       :db         (hikaricp (:db config))
       :ragtime    (ragtime {:resource-path "migrations"}))
      (component/system-using
        {:ragtime  [:db]})))

(defn load-sql-functions
  [system]
  (doseq [source (resauce/resource-dir "planwise/plpgsql")]
    (try
      (let [sql-source (slurp source)]
        (jdbc/execute! (:spec (:db system)) sql-source))
      (catch java.sql.SQLException e
        (fatal "Error loading SQL functions from " (str source))
        (throw e)))))

(defn -main [& args]
  (timbre/set-level! :warn)
  (if-let [cmd (first args)]
    (let [system (new-system config)
          system (component/start system)]
      (println "Loading SQL functions into database")
      (load-sql-functions system)
      (println "Running migrations")
      (case cmd
        "migrate" (migrate (:ragtime system))
        "rollback" (if-let [target (second args)]
                     (rollback (:ragtime system) target)
                     (rollback (:ragtime system)))))
    (println "Run DB command with either migrate or rollback")))
