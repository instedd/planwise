(ns planwise.component.facilities
  (:require [com.stuartsierra.component :as component]
            [clojure.java.jdbc :as jdbc]
            [hugsql.core :as hugsql]
            [clojure.string :refer [join lower-case]]))

;; ----------------------------------------------------------------------
;; Auxiliary and utility functions

(hugsql/def-db-fns "planwise/sql/facilities.sql")

(defn get-db
  "Retrieve the database connection for a service"
  [component]
  (get-in component [:db :spec]))


;; ----------------------------------------------------------------------
;; Service definition

(defrecord FacilitiesService [db])

(defn facilities-service
  "Construct a Facilities Service component"
  []
  (map->FacilitiesService {}))


;; ----------------------------------------------------------------------
;; Service functions

(defn insert-facilities! [service facilities]
  (jdbc/with-db-transaction [tx (get-db service)]
    (doseq [facility facilities]
      (insert-facility! tx facility)))
  (count facilities))

(defn destroy-facilities! [service]
  (delete-facilities! (get-db service)))

(defn list-facilities
  ([service]
   (select-facilities (get-db service)))
  ([service criteria]
   (let [{:keys [types region]} criteria]
     (if (empty? types) []
       (facilities-by-criteria (get-db service) {:types (map lower-case types), :region region})))))

(defn count-facilities-in-region
  [service {region :region}]
  (count-facilities-in-region* (get-db service) {:region region}))

(defn list-with-isochrones
  ([service threshold]
   (list-with-isochrones service threshold "alpha-shape" 0.0))
  ([service threshold method simplify]
   (facilities-with-isochrones (get-db service)
                               {:threshold threshold,
                                :method method,
                                :simplify simplify})))

(defn get-isochrone-for-all-facilities [service threshold]
  (isochrone-for-facilities (get-db service) {:threshold threshold}))
