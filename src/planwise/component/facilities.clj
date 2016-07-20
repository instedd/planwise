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

(defn facilities-criteria [{types :types, :as criteria}]
  (criteria-snip
    (if (nil? types)
      criteria
      (assoc criteria :types (map lower-case types)))))

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
   (facilities-by-criteria
     (get-db service)
     {:criteria (facilities-criteria criteria)})))

(defn list-with-isochrones
  ([service]
   (list-with-isochrones service {} {}))
  ([service isochrone-options]
   (list-with-isochrones service isochrone-options {}))
  ([service {:keys [threshold algorithm simplify]} criteria]
   (facilities-with-isochrones (get-db service)
      {:threshold (or threshold 900)
       :algorithm (or algorithm "alpha-shape")
       :simplify (or simplify 0.0)
       :criteria (facilities-criteria criteria)})))

(defn get-isochrone-for-all-facilities [service threshold]
  (isochrone-for-facilities (get-db service) {:threshold threshold}))

(defn list-types [service]
  (select-types (get-db service)))
