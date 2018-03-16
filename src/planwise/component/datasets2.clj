(ns planwise.component.datasets2
  (:require [planwise.boundary.datasets2 :as boundary]
            [integrant.core :as ig]
            [taoensso.timbre :as timbre]
            [clojure.data.csv :as csv]
            [clojure.java.jdbc :as jdbc]
            [hugsql.core :as hugsql]
            [clojure.edn :as edn]
            [planwise.util.hash :refer [update-if]]
            [clojure.java.io :as io]))

;; ----------------------------------------------------------------------
;; Auxiliary and utility functions

(hugsql/def-db-fns "planwise/sql/datasets2.sql")

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

(defn- import-site
  [store dataset-id version csv-site-data]
  (let [data {:id (Integer. (:id csv-site-data))
              :type (:type csv-site-data)
              :version version
              :dataset-id dataset-id
              :name (:name csv-site-data)
              :lat  (Double. (:lat csv-site-data))
              :lon  (Double. (:lon csv-site-data))
              :capacity (Integer. (:capacity csv-site-data))
              :tags (:tags csv-site-data)}]
     (db-create-site! (get-db store) data)))

(defn csv-to-sites
  "Generates sites from a dataset-id and a csv file"
  [store dataset-id csv-file]
  (let [reader     (io/reader csv-file)
        sites-data (csv-data->maps (csv/read-csv reader))
        version    (:last-version (db-create-dataset-version! (get-db store) {:id dataset-id}))]
    (doall (map #(import-site store dataset-id version %) sites-data))))

(defn sites-by-version
  "Returns sites associated to a dataset-id and version"
  [store dataset-id version]
  (db-find-sites (get-db store) {:dataset-id dataset-id
                                 :version version}))

;; ----------------------------------------------------------------------
;; Service definition

(defn create-dataset
  [store name owner-id]
  (db-create-dataset! (get-db store) {:name name
                                      :owner-id owner-id}))

(defn list-datasets
  [store owner-id]
  (db-list-datasets (get-db store) {:owner-id owner-id}))

(defn get-dataset
  [store dataset-id]
  (first (db-find-dataset (get-db store) {:id dataset-id})))

(defn create-and-import-sites
  [store name owner-id csv-file]
  (jdbc/with-db-transaction [tx (get-db store)]
    (let [tx-store (assoc-in store [:db :spec] tx)
          create-result (create-dataset tx-store name owner-id)
          dataset-id (:id create-result)]
      (csv-to-sites tx-store dataset-id csv-file)
      (get-dataset tx-store dataset-id))))

(defrecord SitesDatasetsStore [db]
  boundary/Datasets2
  (list-datasets [store owner-id]
    (list-datasets store owner-id))
  (get-dataset [store dataset-id]
    (get-dataset store dataset-id))
  (create-dataset [store name owner-id]
    (create-dataset store name owner-id))
  (import-csv [store dataset-id csv-file]
    (csv-to-sites store dataset-id csv-file))
  (create-and-import-sites [store name owner-id csv-file]
    (create-and-import-sites store name owner-id csv-file)))

(defmethod ig/init-key :planwise.component/datasets2
  [_ config]
  (map->SitesDatasetsStore config))
