(ns planwise.component.datasets
  (:require [planwise.boundary.datasets :as boundary]
            [integrant.core :as ig]
            [taoensso.timbre :as timbre]
            [hugsql.core :as hugsql]
            [clojure.edn :as edn]
            [planwise.util.hash :refer [update-if]]))

;; ----------------------------------------------------------------------
;; Auxiliary and utility functions

(hugsql/def-db-fns "planwise/sql/datasets.sql")

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
          (update-if :import-result pr-str)
          (update-if :import-job pr-str)
          (update-if :mappings pr-str)))


;; ----------------------------------------------------------------------
;; Service definition

(defrecord DatasetsStore [db]

  boundary/Datasets
  (list-datasets-for-user [{:keys [db]} user-id]
    (->> (select-datasets-for-user (:spec db) {:user-id user-id})
         (map db->dataset)))
  (list-datasets-with-import-jobs [{:keys [db]}]
    (->> (select-datasets-with-import-jobs (:spec db))
         (map db->dataset)
         (filter (comp some? :import-job))))
  (create-dataset! [{:keys [db]} dataset]
    (let [dataset-id (->> dataset
                          dataset->db
                          (insert-dataset! (:spec db))
                          :id)]
      (assoc dataset
             :id dataset-id
             :facility-count 0
             :project-count 0)))
  (find-dataset [{:keys [db]} dataset-id]
    (-> (select-dataset (:spec db) {:id dataset-id})
        db->dataset))
  (update-dataset [{:keys [db]} dataset]
    (update-dataset* (:spec db) (dataset->db dataset)))
  (destroy-dataset! [{:keys [db]} dataset-id]
    (delete-dataset! (:spec db) {:id dataset-id}))
  (accessible-by? [{:keys [db]} dataset user-id]
    (or
     (= (:owner-id dataset) user-id)
     (let [args {:user-id user-id :dataset-id (:id dataset)}]
       (-> (count-accessible-projects-for-dataset (:spec db) args)
           :count
           pos?)))))


;; ----------------------------------------------------------------------
;; Service initialization

(defmethod ig/init-key :planwise.component/datasets
  [_ config]
  (map->DatasetsStore config))

