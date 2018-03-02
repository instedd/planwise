(ns planwise.database
  (:require [integrant.core :as ig]
            [resauce.core :as resauce]
            [ragtime.jdbc :as rag-jdbc]
            [clojure.java.jdbc :as jdbc]
            [clojure.string :as str]
            [pandect.algo.sha1 :refer [sha1]]
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
  (info "Loading PL/SQL functions into database")
  (doseq [source (resauce/resource-dir "planwise/plpgsql")]
    (load-and-execute-sql database source)))

(defn create-osm2pgr-tables
  [database]
  (load-and-execute-sql database "test/scripts/osm2pgr-tables.sql"))

(defn table-exists?
  [database table]
  (-> (:spec database)
      (jdbc/query ["SELECT 1 FROM information_schema.tables WHERE table_schema = 'public' AND table_name = ?" table])
      empty?
      not))

(def ^:private colon (.getBytes ":"  "US-ASCII"))
(def ^:private comma (.getBytes ","  "US-ASCII"))
(def ^:private u=    (.getBytes "u=" "US-ASCII"))
(def ^:private d=    (.getBytes "d=" "US-ASCII"))

(defn- netstring [bs]
  (let [size (.getBytes (str (count bs)) "US-ASCII")]
    (byte-array (concat size colon bs comma))))

(defn- get-bytes [s]
  (.getBytes s "UTF-8"))

(defn- coll->netstring [coll]
  (netstring (mapcat (comp netstring get-bytes) coll)))

(defn- hash-migration [{:keys [up down]}]
  (sha1 (byte-array (concat u= (coll->netstring up)
                            d= (coll->netstring down)))))

(defn- add-hash-to-id [migration]
  (update migration :id str "#" (subs (hash-migration migration) 0 8)))

(defmethod ig/init-key :planwise.database/migrations
  [_ [path]]
  (->> (rag-jdbc/load-resources path)
       (map add-hash-to-id)))

(defmethod ig/init-key :planwise.database/pre-init
  [_ {:keys [db]}]
  (load-sql-functions db))
