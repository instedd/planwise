(ns planwise.component.datasets2
  (:require [integrant.core :as ig]
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

;; ----------------------------------------------------------------------
;; Service definition

(defrecord SitesDatasetsStore [db])

(defn datasets2-store
  "Constructs a Datasets2 Store component"
  []
  (map->SitesDatasetsStore {}))

(defn create-dataset
  [store name owner-id]
  (db-create-dataset! (get-db store) {:name name
                                      :owner-id owner-id}))

(defn list-datasets
  [store owner-id]
  (db-list-datasets (get-db store) {:owner-id owner-id}))

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
  (let [reader          (io/reader csv-file)
        sites-data (csv-data->maps (csv/read-csv reader))
        version         (:last-version (db-create-dataset-version! (get-db store) {:id dataset-id}))]
    (doall (map #(import-site store dataset-id version %) sites-data))))

(defn sites-by-version
  "Returns sites associated to a dataset-id and version"
  [store dataset-id version]
  (db-find-sites (get-db store) {:dataset-id dataset-id
                                 :version version}))

(defn get-dataset
  [store dataset-id]
  (first (db-find-dataset (get-db store) {:id dataset-id})))

(defmethod ig/init-key :planwise.component/datasets2
  [_ config]
  (map->SitesDatasetsStore config))
