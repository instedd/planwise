(ns planwise.test-system
  (:require [planwise.database :as database]
            [integrant.core :as ig]
            [clojure.java.jdbc :as jdbc]
            [clojure.java.io :as io]
            [duct.core :as duct]
            [duct.logger :as logger]
            [taoensso.timbre :as timbre]))

(timbre/refer-timbre)

(duct/load-hierarchy)

;; Logging configuration for development
(timbre/merge-config! {:level :debug
                       :ns-blacklist ["com.zaxxer.hikari.*"
                                      "org.apache.http.*"
                                      "org.eclipse.jetty.*"]})

(defrecord TestLogger [logs]
  logger/Logger
  (-log [_ level ns-str file line id event data]
    (swap! logs conj [event data])))

(def logs
  (atom []))

(defmethod ig/init-key :planwise.test/logger
  [_ config]
  (->TestLogger logs))

(derive :planwise.test/logger :duct/logger)

;; This is injected as a dependency to duct.migrator/ragtime to make sure it's
;; initialized *before* running the migrations
(defmethod ig/init-key :planwise.test/db-pre-setup
  [_ {:keys [db]}]
  (database/create-osm2pgr-tables db)
  (database/load-sql-functions db))

(defn- load-table!
  [spec table records]
  (jdbc/insert-multi! spec table records))

(defn- clear-table!
  [spec table]
  (jdbc/delete! spec table []))

(defn- unload-fixtures!
  [spec fixtures]
  (doseq [[table _] (reverse fixtures)]
    (clear-table! spec table)))

(defmethod ig/init-key :planwise.test/fixtures
  [_ {:keys [db fixtures]}]
  (if (nil? db)
    (throw (IllegalArgumentException. "Missing :db configuration for fixtures")))
  (let [spec (:spec db)]
    (unload-fixtures! spec fixtures)
    (doseq [[table records] fixtures]
      (when (seq records)
        (load-table! spec table records))))
  {:db       db
   :fixtures fixtures})

(defmethod ig/halt-key! :planwise.test/fixtures
  [_ {:keys [db fixtures]}]
  (when (some? db)
    (let [spec (:spec db)]
      (unload-fixtures! spec fixtures))))

(defn config
  ([]
   (config {}))
  ([other]
   (-> (io/resource "test.edn")
       duct/read-config
       (duct/merge-configs other))))

;; REPL code to check that the base test system can be started successfully
(comment (let [config (config)
               prepped (duct/prep config)
               system (ig/init prepped)]
           (ig/halt! system)))

(defmacro with-system
  "Execute body with a system initialized from the configuration in the first
  argument and bound to the 'system' symbol, and stop it after the execution is
  complete."
  [config & body]
  `(let [~'system (ig/init (duct/prep ~config))]
     (try
       ~@body
       (finally
         (ig/halt! ~'system)))))

(defn execute-sql
  [system sql]
  (let [[_ db] (ig/find-derived-1 system :duct.database/sql)]
    (jdbc/execute! (:spec db) sql)))
