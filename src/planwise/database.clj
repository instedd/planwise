(ns planwise.database
  (:require [resauce.core :as resauce]
            [clojure.java.jdbc :as jdbc]
            [taoensso.timbre :as timbre]))

(timbre/refer-timbre)

(defn load-and-execute-sql
  [database source]
  (try
    (let [sql-source (slurp source)]
      (jdbc/execute! (:spec database) sql-source))
    (catch java.sql.SQLException e
      (fatal "Error loading/executing SQL file " (str source))
      (throw e))))

(defn load-sql-functions
  [database]
  (doseq [source (resauce/resource-dir "planwise/plpgsql")]
    (load-and-execute-sql database source)))

(defn create-osm2pgr-tables
  [database]
  (load-and-execute-sql database "test/scripts/osm2pgr-tables.sql"))
