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
          (update :mappings edn/read-string)))

(defn dataset->db
  [dataset]
  (some-> dataset
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
                        :id)
        dataset (assoc dataset :id dataset-id)]
    dataset))

(defn find-dataset
  [store dataset-id]
  (let [db (get-db store)]
    (-> (select-dataset db {:id dataset-id})
        db->dataset)))
