(ns planwise.component.facilities
  (:require [com.stuartsierra.component :as component]
            [planwise.component.runner :refer [run-external]]
            [planwise.util.str :refer [trim-to-int]]
            [clojure.java.jdbc :as jdbc]
            [hugsql.core :as hugsql]
            [taoensso.timbre :as timbre]
            [clojure.string :refer [trim trim-newline join lower-case]]))

(timbre/refer-timbre)

;; ----------------------------------------------------------------------
;; Auxiliary and utility functions

(hugsql/def-db-fns "planwise/sql/facilities.sql")

(defn get-db
  "Retrieve the database connection for a service"
  [component]
  (get-in component [:db :spec]))

(defn- isochrone-params [{:keys [threshold algorithm simplify]}]
  {:threshold (or threshold 900)
   :algorithm (or algorithm "alpha-shape")
   :simplify  (or simplify 0.001)})


;; ----------------------------------------------------------------------
;; Service definition

(defrecord FacilitiesService [config db runner])

(defn facilities-service
  "Construct a Facilities Service component"
  [config]
  (map->FacilitiesService {:config config}))


;; ----------------------------------------------------------------------
;; Service functions

(defn insert-facilities! [service dataset-id facilities]
  (jdbc/with-db-transaction [tx (get-db service)]
    (delete-facilities-in-dataset-by-site-id! tx {:dataset-id dataset-id, :site-ids (map :site-id facilities)})
    (reduce (fn [ids facility]
              (let [result (insert-facility! tx (assoc facility :dataset-id dataset-id))]
                (conj ids (:id result))))
            []
            facilities)))

(defn destroy-facilities! [service dataset-id]
  (delete-facilities-in-dataset! (get-db service) {:dataset-id dataset-id}))

(defn list-facilities
  ([service dataset-id]
   (select-facilities-in-dataset (get-db service) {:dataset-id dataset-id}))
  ([service dataset-id criteria]
   (facilities-in-dataset-by-criteria
     (get-db service)
     {:dataset-id dataset-id
      :criteria (criteria-snip criteria)})))

(defn count-facilities
  ([service dataset-id]
   (count-facilities service dataset-id {}))
  ([service dataset-id criteria]
   (let [db (get-db service)
         criteria (criteria-snip criteria)
         result (count-facilities-in-dataset-by-criteria db {:dataset-id dataset-id
                                                             :criteria criteria})]
     (:count result))))

(defn isochrones-in-bbox
  ([service dataset-id isochrone-opts criteria]
   (isochrones-for-dataset-in-bbox* (get-db service)
     (-> (isochrone-params isochrone-opts)
         (merge (select-keys criteria [:bbox :excluding]))
         (assoc :dataset-id dataset-id
                :criteria (criteria-snip criteria))))))

(defn polygons-in-region
  [service dataset-id isochrone-options criteria]
  (select-polygons-in-region (get-db service)
                             (assoc (isochrone-params isochrone-options)
                                    :dataset-id dataset-id
                                    :region-id (:region criteria)
                                    :criteria (criteria-snip criteria))))

(defn list-types [service dataset-id]
  (select-types-in-dataset (get-db service) {:dataset-id dataset-id}))

(defn destroy-types!
  [service dataset-id]
  (delete-types-in-dataset! (get-db service) {:dataset-id dataset-id}))

(defn insert-types!
  [service dataset-id types]
  (jdbc/with-db-transaction [tx (get-db service)]
    (-> (map (fn [type]
               (let [type-id (insert-type! tx (assoc type :dataset-id dataset-id))]
                 (merge type type-id)))
             types)
        (vec))))

(defn raster-isochrones! [service facility-id]
  (let [scale-resolution 8
        facilities-polygons-regions (select-facilities-polygons-regions-for-facility (get-db service) {:facility-id facility-id})]
    (doseq [{:keys [facility-polygon-id region-id] :as fpr} facilities-polygons-regions]
      (try
        (let [population (-> (run-external (:runner service) :scripts 60000 "raster-isochrone" (str region-id) (str facility-polygon-id) (str scale-resolution))
                             (trim-to-int))]
          (set-facility-polygon-region-population!
            (get-db service)
            (assoc fpr :population population)))
        (catch Exception e
          (error e "Error on raster-isochrone for facility" facility-id "polygon" facility-polygon-id "region" region-id)
          nil)))))

(defn calculate-isochrones-population! [service facility-id country]
  (let [facilities-polygons (select-facilities-polygons-for-facility (get-db service) {:facility-id facility-id})]
    (doseq [{facility-polygon-id :facility-polygon-id, :as fp} facilities-polygons]
      (try
        (let [population (-> (run-external (:runner service) :scripts 60000 "isochrone-population" (str facility-polygon-id) country)
                             (trim-to-int))]
          (set-facility-polygon-population!
            (get-db service)
            (assoc fp :population population)))
        (catch Exception e
          (error e "Error on calculate-isochrones-population for facility" facility-id "polygon" facility-polygon-id)
          nil)))))

(defn preprocess-isochrones
 ([service]
  (let [ids (map :id (select-unprocessed-facilities-ids (get-db service)))]
    (doall (mapv (partial preprocess-isochrones service) ids))))
 ([service facility-id]
  (let [[{code :code country :country}] (calculate-facility-isochrones! (get-db service)
                                                           {:id facility-id
                                                            :method "alpha-shape"
                                                            :start 30
                                                            :end 180
                                                            :step 15})
        success? (= "ok" code)
        raster-isochrones? (get-in service [:config :raster-isochrones])]

    (debug (str "Facility " facility-id " isochrone processed with result: " (str code)))
    (when (and success? raster-isochrones?)
      (calculate-isochrones-population! service facility-id country)
      (raster-isochrones! service facility-id))

    (keyword code))))

(defn clear-facilities-processed-status!
  [service]
  (clear-facilities-processed-status* (get-db service)))
