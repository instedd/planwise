(ns planwise.component.sites-datasets
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

(hugsql/def-db-fns "planwise/sql/sites_datasets.sql")

(defn get-db
  [store]
  (get-in store [:db :spec]))

;; ----------------------------------------------------------------------
;; Service definition

(defrecord SitesDatasetsStore [db])

(defn sites-datasets-store
  "Constructs a SitesDatasets Store component"
  []
  (map->SitesDatasetsStore {}))

(defn create-sites-dataset
  [store name owner-id]
  (create-dataset! (get-db store) {:name name
                                   :owner-id owner-id}))

(defn list-sites-datasets
  [store owner-id]
  (list-datasets (get-db store) {:owner-id owner-id}))

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
     (create-site! (get-db store) data)))

(defn csv-to-facilities
  "Generates facilities from a dataset-id and a csv file"
  [store dataset-id csv-file]
  (let [reader          (io/reader csv-file)
        facilities-data (csv-data->maps (csv/read-csv reader))
        version         (:last-version (create-dataset-version! (get-db store) {:id dataset-id}))]
    (doall (map #(import-site store dataset-id version %) facilities-data))))

(defn find-sites-facilities
  "Returns sites associated to a dataset-id and version"
  [store dataset-id version]
  (find-sites (get-db store) {:dataset-id dataset-id
                              :version version}))

(defn get-dataset
  [store dataset-id]
  (first (find-dataset (get-db store) {:id dataset-id})))

(defmethod ig/init-key :planwise.component/sites-datasets
  [_ config]
  (map->SitesDatasetsStore config))
