(ns planwise.component.datasets
  (:require [com.stuartsierra.component :as component]
            [taoensso.timbre :as timbre]
            [hugsql.core :as hugsql]
            [clojure.edn :as edn]))

;; ----------------------------------------------------------------------
;; Auxiliary and utility functions

(hugsql/def-db-fns "planwise/sql/datasets.sql")

(defn get-db [store]
  (get-in store [:db :spec]))

;; Mapper functions

(defn db->dataset
  [record]
  (some-> record
          (update :import-result edn/read-string)
          (update :mappings edn/read-string)))

(defn dataset->db
  [dataset]
  (some-> dataset
          (update :import-result pr-str)
          (update :mappings pr-str)))


;; ----------------------------------------------------------------------
;; Service definition

(defrecord DatasetsStore [db])

(defn datasets-store
  "Constructs a Datasets Store component"
  []
  (map->DatasetsStore {}))

(defn list-datasets-for-user
  [store user-id]
  (select-datasets-for-user (get-db store) {:user-id user-id}))

(defn create-dataset!
  [store dataset]
  (let [db (get-db store)
        dataset-id (->> dataset
                        dataset->db
                        (insert-dataset! db)
                        :id)]
    (assoc dataset
           :id dataset-id
           :facility-count 0)))

(defn find-dataset
  [store dataset-id]
  (let [db (get-db store)]
    (-> (select-dataset db {:id dataset-id})
        db->dataset)))

(defn update-dataset
  [store dataset]
  (update-dataset* (get-db store) dataset))

(defn destroy-dataset!
  [store dataset-id]
  (delete-dataset! (get-db store) {:id dataset-id}))
