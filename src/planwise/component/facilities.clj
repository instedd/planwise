(ns planwise.component.facilities
  (:require [com.stuartsierra.component :as component]
            [planwise.component.runner :refer [run-external]]
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

(defn facilities-criteria [criteria]
  (criteria-snip criteria))

(defn raster-facility-isochrones! [{runner :runner} facility-id]
  (run-external runner :scripts "raster-isochrones" (str facility-id)))

;; ----------------------------------------------------------------------
;; Service definition

(defrecord FacilitiesService [db runner])

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

(defn count-facilities
  ([service]
   (count-facilities service {}))
  ([service criteria]
   (let [db (get-db service)
         criteria (facilities-criteria criteria)
         result (count-facilities-by-criteria db {:criteria criteria})]
     (:count result))))

(defn list-with-isochrones
  ([service]
   (list-with-isochrones service {} {}))
  ([service isochrone-options]
   (list-with-isochrones service isochrone-options {}))
  ([service {:keys [threshold algorithm simplify]} criteria]
   (facilities-with-isochrones (get-db service)
      {:threshold (or threshold 900)
       :algorithm (or algorithm "alpha-shape")
       :simplify (or simplify 0.001)
       :criteria (facilities-criteria criteria)})))

(defn get-isochrone-for-all-facilities [service threshold]
  (isochrone-for-facilities (get-db service) {:threshold threshold}))

(defn list-types [service]
  (select-types (get-db service)))

(defn destroy-types!
  [service]
  (delete-types! (get-db service)))

(defn insert-types!
  [service types]
  (jdbc/with-db-transaction [tx (get-db service)]
    (-> (map (fn [type]
               (let [type-id (insert-type! tx type)]
                 (merge type type-id)))
             types)
        (vec))))


(defn preprocess-isochrones
  [service facility-id]
  (->
    (calculate-facility-isochrones! (get-db service)
                                  {:id facility-id
                                   :method "alpha-shape"
                                   :start 30
                                   :end 180
                                   :step 15})
    first
    :process_facility_isochrones)
  ; TODO: Return :process_facility_isochrones
  (raster-facility-isochrones! service facility-id))
