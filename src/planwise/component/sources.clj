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
  [store name unit owner-id]
  (db-create-source-set! (get-db store) {:name name
                                         :unit unit
                                         :owner-id owner-id}))

(defn create-source
  [store source]
  (db-create-source! (get-db store) source))

(defn get-source-set
  [store id owner-id]
  (db-find-source-set (get-db store) {:id id
                                      :owner-id owner-id}))

(defn import-source
  [store set-id csv-source-data]
  (let [source {:set-id set-id
                :name (:name csv-source-data)
                :type (:type csv-source-data)
                :lat  (Double. (:lat csv-source-data))
                :lon  (Double. (:lon csv-source-data))
                :quantity (Integer. (:unsatisfied csv-source-data))}]
    (create-source store source)))

(defn import-sources
  [store set-id csv-file]
  (let [reader (io/reader csv-file)
        csv-data (csv-data->maps (csv/read-csv reader))]
    (doall (map #(import-source store set-id %) csv-data))
    set-id))

(defn import-from-csv
  [store {:keys [name unit owner-id]} csv-file]
  (jdbc/with-db-transaction [tx (get-db store)]
    (let [tx-store    (assoc-in store [:db :spec] tx)
          source-set  (create-source-set tx-store name unit owner-id)
          source-set-id (:id source-set)]
      (import-sources tx-store source-set-id csv-file)
      (get-source-set tx-store source-set-id owner-id))))

;; ----------------------------------------------------------------------
;; Store

(defrecord SourcesStore [db]
  boundary/Sources
  (list-sources [store owner-id]
    (list-sources store owner-id))
  (import-from-csv [store options csv-file]
    (import-from-csv store options csv-file)))

;; ----------------------------------------------------------------------
;;

(defmethod ig/init-key :planwise.component/sources
  [_ config]
  (map->SourcesStore config))
