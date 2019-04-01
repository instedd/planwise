(ns planwise.component.sources
  (:require [planwise.boundary.sources :as boundary]
            [integrant.core :as ig]
            [taoensso.timbre :as timbre]
            [clojure.java.io :as io]
            [clojure.data.csv :as csv]
            [hugsql.core :as hugsql]
            [clojure.java.jdbc :as jdbc]
            [clojure.data.json :as json]))

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

(defn get-source-set-by-id
  [store id]
  (db-find-source-set-by-id (get-db store) {:id id}))

(defn import-source
  [store set-id csv-source-data]
  (let [source {:set-id set-id
                :name (:name csv-source-data)
                :type (:type csv-source-data)
                :lat  (Double. (:lat csv-source-data))
                :lon  (Double. (:lon csv-source-data))
                :quantity (Integer. (:unsatisfied-demand csv-source-data))}]
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

(defn list-sources-in-set
  [store source-set-id]
  (db-list-sources-in-set (get-db store) {:source-set-id source-set-id}))

(defn get-sources-from-set-in-region
  [store source-set-id region-id]
  (db-get-sources-from-set-in-region (get-db store) {:source-set-id source-set-id
                                                     :region-id     region-id}))

(defn enum-sources-under-coverage
  [store source-set-id coverage-geom]
  (map :id
       (db-enum-sources-under-coverage (get-db store)
                                       {:source-set-id source-set-id
                                        :coverage-geom coverage-geom})))

(defn- get-sources-extent
  [store source-ids]
  (when (seq source-ids)
    (:extent (db-get-sources-extent (get-db store) {:source-ids source-ids}))))

;; ----------------------------------------------------------------------
;; Store

(defrecord SourcesStore [db]
  boundary/Sources
  (list-sources [store owner-id]
    (list-sources store owner-id))
  (import-from-csv [store options csv-file]
    (import-from-csv store options csv-file))
  (get-source-set-by-id [store id]
    (get-source-set-by-id store id))
  (list-sources-in-set [this source-set-id]
    (list-sources-in-set this source-set-id))
  (get-sources-from-set-in-region [this source-set-id region-id]
    (get-sources-from-set-in-region this source-set-id region-id))
  (enum-sources-under-coverage [this source-set-id coverage-geom]
    (enum-sources-under-coverage this source-set-id coverage-geom))
  (get-sources-extent [this ids]
    (get-sources-extent this ids)))

;; ----------------------------------------------------------------------
;;

(defmethod ig/init-key :planwise.component/sources
  [_ config]
  (map->SourcesStore config))
