(ns planwise.component.regions
  (:require [planwise.boundary.regions :as boundary]
            [integrant.core :as ig]
            [clojure.data.json :as json]
            [hugsql.core :as hugsql]))

;; ----------------------------------------------------------------------
;; Auxiliary and utility functions

(hugsql/def-db-fns "planwise/sql/regions.sql")

(defn db->region [{json-bbox :bbox :as record}]
  (if json-bbox
    (let [coordinates (-> json-bbox json/read-str (get-in ["coordinates" 0]))
          [se ne nw sw se'] (map reverse coordinates)]
      (assoc record :bbox [sw ne]))
    record))


;; ----------------------------------------------------------------------
;; Service definition

(defrecord RegionsService [db]

  boundary/Regions
  (list-regions [{:keys [db]}]
    (map db->region (select-regions (:spec db))))
  (list-regions-with-preview [{:keys [db]} ids]
    (map db->region
         (select-regions-with-preview-given-ids (:spec db) {:ids ids})))
  (list-regions-with-geo [{:keys [db]} ids simplify]
    (map db->region
         (select-regions-with-geo-given-ids (:spec db)
                                            {:ids ids :simplify simplify})))
  (find-region [{:keys [db]} id]
    (db->region (select-region (:spec db) {:id id})))

  (enum-regions-inside-envelope [{:keys [db]} envelope]
    (map :id (region-ids-inside-envelope (:spec db) envelope))))


;; ----------------------------------------------------------------------
;; Service initialization

(defmethod ig/init-key :planwise.component/regions
  [_ config]
  (map->RegionsService config))
