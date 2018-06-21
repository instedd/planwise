(ns planwise.component.sources
  (:require [planwise.boundary.sources :as boundary]
            [integrant.core :as ig]
            [taoensso.timbre :as timbre]
            [clojure.java.io :as io]
            [clojure.data.csv :as csv]
            [hugsql.core :as hugsql]
            [clojure.java.jdbc :as jdbc]))

;; ----------------------------------------------------------------------
;; Auxiliary and utility functions

(hugsql/def-db-fns "planwise/sql/sources.sql")

(defn get-db
  [store]
  (get-in store [:db :spec]))

(defn- csv-data->maps
  [csv-data]
  (map zipmap
       (->> (first csv-data)
            (map keyword)
            repeat)
       (rest csv-data)))

;; ----------------------------------------------------------------------
;; Service definition

(defn list-sources
  [store owner-id]
  (db-list-sources (get-db store) {:owner-id owner-id}))

(defn create-source-set
  [store name owner-id]
  (db-create-source-set! (get-db store) {:name name
                                         :owner-id owner-id}))

(defn get-source-set
  [store id owner-id]
  (println "get-source-set")
  (println id)
  (println owner-id)
  (db-find-source-set (get-db store) {:id id}))


(defn import-sources
  [store set-id csv-file]
  (let [reader (io/reader csv-file)]
    (println (csv-data->maps (csv/read-csv reader))))
  set-id)

(defn import-from-csv
  [store {:keys [name owner-id]} csv-file]
  (jdbc/with-db-transaction [tx (get-db store)]
    (let [tx-store                  (assoc-in store [:db :spec] tx)
          tx-create-source-set      (fn [name] (let [result (create-source-set tx-store name owner-id)]
                                                 (:id result)))
          tx-import-sources-from    (fn [set-id csv-file] (import-sources tx-store set-id csv-file))
          tx-get-created-source-set (fn [set-id] (get-source-set tx-store set-id owner-id))]
      (-> (tx-create-source-set name)
          (tx-import-sources-from csv-file)
          (tx-get-created-source-set)))))

;; ----------------------------------------------------------------------
;; Store

(defrecord SourcesStore [db]
  boundary/Sources
  (list-sources [store owner-id]
    (list-sources [store owner-id]))
  (import-from-csv [store options csv-file]
    (import-from-csv [store options csv-file])))

;; ----------------------------------------------------------------------
;;

(defmethod ig/init-key :planwise.component/sources
  [_ config]
  (map->SourcesStore config))
