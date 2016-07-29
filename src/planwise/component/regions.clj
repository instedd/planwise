(ns planwise.component.regions
  (:require [clojure.java.jdbc :as jdbc]
            [com.stuartsierra.component :as component]
            [clojure.data.json :as json]
            [clojure.set :as set]
            [hugsql.core :as hugsql]))

;; ----------------------------------------------------------------------
;; Auxiliary and utility functions

(hugsql/def-db-fns "planwise/sql/regions.sql")

(defn get-db [service]
  (get-in service [:db :spec]))

(defn db->region [{json-bbox :bbox, :as record}]
  (let [[se ne nw sw se'] (map reverse (get-in (json/read-str json-bbox) ["coordinates" 0]))]
    (-> record
      (assoc :bbox [sw ne])
      (set/rename-keys {:admin_level :admin-level
                        :preview_geojson :preview-geojson}))))


;; ----------------------------------------------------------------------
;; Service definition

(defrecord RegionsService [db])

(defn regions-service
  "Constructs a Regions service component"
  []
  (map->RegionsService {}))


;; ----------------------------------------------------------------------
;; Service functions

(defn list-regions [service]
  (map db->region
    (select-regions (get-db service))))

(defn list-regions-with-preview [service ids]
  (map db->region
    (select-regions-with-preview-given-ids (get-db service) {:ids ids})))

(defn list-regions-with-geo [service ids simplify]
  (map db->region
    (select-regions-with-geo-given-ids (get-db service) {:ids ids, :simplify simplify})))
