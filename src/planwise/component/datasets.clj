(ns planwise.component.datasets
  (:require [com.stuartsierra.component :as component]
            [taoensso.timbre :as timbre]
            [hugsql.core :as hugsql]
            [clojure.edn :as edn]
            [planwise.util.hash :refer [update-if-contains]]))

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
          (update :import-job edn/read-string)
          (update :mappings edn/read-string)))

(defn dataset->db
  [dataset]
  (some-> dataset
          (update-if-contains :import-result #(some-> % pr-str))
          (update-if-contains :import-job #(some-> % pr-str))
          (update-if-contains :mappings #(some-> % pr-str))))


;; ----------------------------------------------------------------------
;; Service definition

(defrecord DatasetsStore [db])

(defn datasets-store
  "Constructs a Datasets Store component"
  []
  (map->DatasetsStore {}))

(defn list-datasets-for-user
  [store user-id]
  (->> (select-datasets-for-user (get-db store) {:user-id user-id})
       (map db->dataset)))

(defn list-datasets-with-import-jobs
  [store]
  (->> (select-datasets-with-import-jobs (get-db store))
       (map db->dataset)
       (filter (comp some? :import-job))))

(defn create-dataset!
  [store dataset]
  (let [db (get-db store)
        dataset-id (->> dataset
                        dataset->db
                        (insert-dataset! db)
                        :id)]
    (assoc dataset
           :id dataset-id
           :facility-count 0
           :project-count 0)))

(defn find-dataset
  [store dataset-id]
  (let [db (get-db store)]
    (-> (select-dataset db {:id dataset-id})
        db->dataset)))

(defn update-dataset
  [store dataset]
  (update-dataset* (get-db store) (dataset->db dataset)))

(defn destroy-dataset!
  [store dataset-id]
  (delete-dataset! (get-db store) {:id dataset-id}))


(defn accessible-by?
  [store dataset user-id]
  (or
    (= (:owner-id dataset) user-id)
    (let [args {:user-id user-id :dataset-id (:id dataset)}]
      (-> (count-accessible-projects-for-dataset (get-db store) args)
        (:count)
        (pos?)))))
