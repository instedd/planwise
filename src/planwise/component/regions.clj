(ns planwise.component.regions
  (:require [clojure.java.jdbc :as jdbc]
            [com.stuartsierra.component :as component]
            [clojure.data.json :as json]
            [hugsql.core :as hugsql]))

;; ----------------------------------------------------------------------
;; Auxiliary and utility functions

(hugsql/def-db-fns "planwise/sql/regions.sql")

(defn get-db [service]
  (get-in service [:db :spec]))

(defn wrap-region [{json-bbox :bbox, :as region}]
  (let [[se ne nw sw se'] (map reverse (get-in (json/read-str json-bbox) ["coordinates" 0]))]
    (assoc region :bbox [sw ne])))


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
  (map wrap-region
    (select-regions (get-db service))))

(defn list-regions-with-geo [service ids simplify]
  (map wrap-region
    (select-regions-with-geo-given-ids (get-db service) {:ids ids, :simplify simplify})))
