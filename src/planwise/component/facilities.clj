(ns planwise.component.facilities
  (:require [com.stuartsierra.component :as component]
            [clojure.java.jdbc :as jdbc]
            [hugsql.core :as hugsql]
            [clojure.string :refer [join lower-case]]))

;; ----------------------------------------------------------------------
;; Auxiliary and utility functions

(hugsql/def-db-fns "planwise/sql/facilities.sql")

(defn get-db [component]
  "Retrieve the database connection for a service"
  (get-in component [:db :spec]))


;; ----------------------------------------------------------------------
;; Service definition

(defrecord FacilitiesService [db])

(defn facilities-service []
  "Construct a Facilities Service component"
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

(defn list-facilities [service]
  (select-facilities (get-db service)))

(defn list-facilities-with-types [service types]
  (if (nil? types)
    []
    (facilities-with-types (get-db service) {:types (map lower-case types)})))

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

