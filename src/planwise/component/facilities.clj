(ns planwise.component.facilities
  (:require [planwise.boundary.facilities :as boundary]
            [planwise.boundary.runner :as runner]
            [integrant.core :as ig]
            [planwise.util.str :refer [trim-to-int]]
            [planwise.util.collections :refer [find-by]]
            [planwise.util.hash :refer [update-if]]
            [planwise.util.numbers :refer [float=]]
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
;; Service functions

(defn insert-facilities!
  "Upserts the facilities, identifying them by dataset-id and site-id,
   returning a list of the facilities with their ids, plus a new
   :insertion-status, where state is one of :existing, :updated, :moved or :new."
  [service dataset-id facilities]
  (jdbc/with-db-transaction [tx (get-db service)]
    (let [updateable-keys [:name :type-id :capacity]
          updateable-keys-with-pos (concat updateable-keys [:lat :lon])
          site-ids (map :site-id facilities)
          existing-facilities (when (seq site-ids)
                                (facilities-in-dataset-by-criteria tx
                                  {:dataset-id dataset-id
                                   :criteria (criteria-snip {:site-ids site-ids})}))]
      (->> facilities
        (mapv (fn [facility]
                (let [{id :id, :as existing} (find-by existing-facilities :site-id (:site-id facility))
                      position-unchanged?    (and (seq existing)
                                               (float= (:lat existing) (:lat facility))
                                               (float= (:lon existing) (:lon facility)))]

                  (cond
                    ; Insert new record if no existing facility with the site id was found
                    (nil? existing)
                    (let [result (insert-facility! tx (assoc facility :dataset-id dataset-id))]
                      (-> facility
                        (assoc :dataset-id dataset-id
                               :id (:id result)
                               :insertion-status :new)
                        (update :processing-status identity)))

                    ; Check if we need to update any attribute
                    (and position-unchanged?
                      (= (select-keys existing updateable-keys)
                         (select-keys facility updateable-keys)))
                    (assoc existing :insertion-status :existing)

                    ; Check if the position did not change
                    position-unchanged?
                    (let [merged-facility (merge existing
                                                 (select-keys facility updateable-keys))]
                      (update-facility* tx merged-facility)
                      (assoc merged-facility :insertion-status :updated))

                    ; If the position changed, clear processing status
                    :else
                    (let [merged-facility (merge existing
                                                 (select-keys facility updateable-keys-with-pos)
                                                 {:processing-status nil})]
                      (update-facility* tx merged-facility)
                      (assoc merged-facility :insertion-status :moved))))))))))

(defn destroy-facilities! [service dataset-id & [{except-ids :except-ids}]]
  (delete-facilities-in-dataset! (get-db service) {:dataset-id dataset-id
                                                   :except-ids except-ids}))

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
  [service dataset-id & [{except-ids :except-ids}]]
  (delete-types-in-dataset! (get-db service) {:dataset-id dataset-id
                                              :except-ids except-ids}))

(defn insert-types!
  [service dataset-id types]
  (jdbc/with-db-transaction [tx (get-db service)]
    (let [current-types (select-types-in-dataset tx {:dataset-id dataset-id})]
      (->> types
        (mapv (fn [type]
                (let [existing-type (find-by current-types :code (:code type))]
                  (cond
                    ; No previous facility type with that code exists, create a new one
                    (nil? existing-type)
                    (let [type-id (insert-type! tx (assoc type :dataset-id dataset-id))]
                      (merge type type-id))

                    ; Facility type has not changed, do nothing
                    (= (:name existing-type)
                       (:name type))
                    existing-type

                    ; The name has changed, update it
                    :else
                    (do
                      (update-type! tx {:id (:id existing-type)
                                        :name (:name type)})
                      (merge existing-type type))))))))))


(defn raster-isochrones! [service facility-id]
  (let [scale-resolution 8
        facilities-polygons-regions (select-facilities-polygons-regions-for-facility (get-db service) {:facility-id facility-id})]
    (doseq [{:keys [facility-polygon-id region-id] :as fpr} facilities-polygons-regions]
      (try
        (let [population (-> (runner/run-external (:runner service) :scripts 60000 "raster-isochrone"
                                                  [(str region-id) (str facility-polygon-id) (str scale-resolution)])
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
        (let [population (-> (runner/run-external (:runner service) :scripts 60000 "isochrone-population"
                                                  [(str facility-polygon-id) country])
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


;; ----------------------------------------------------------------------
;; Service definition

(defrecord FacilitiesService [config db runner]
  boundary/Facilities
  (list-facilities [service dataset-id]
    (list-facilities service dataset-id))
  (list-facilities [service dataset-id criteria]
    (list-facilities service dataset-id criteria))
  (isochrones-in-bbox [service dataset-id isochrone-options facilities-criteria]
    (isochrones-in-bbox service dataset-id isochrone-options facilities-criteria))
  (polygons-in-region [service dataset-id isochrone-options facilities-criteria]
    (polygons-in-region service dataset-id isochrone-options facilities-criteria))
  (list-types [service dataset-id]
    (list-types service dataset-id))
  (count-facilities [service dataset-id criteria]
    (count-facilities service dataset-id criteria))
  (insert-types! [service dataset-id types]
    (insert-types! service dataset-id types))
  (insert-facilities! [service dataset-id facilities]
    (insert-facilities! service dataset-id facilities))
  (preprocess-isochrones [service id]
    (preprocess-isochrones service id))
  (clear-facilities-processed-status! [service facilities]
    (clear-facilities-processed-status! service facilities))
  (destroy-facilities! [service dataset-id options]
    (destroy-facilities! service dataset-id options))
  (destroy-types! [service dataset-id options]
    (destroy-types! service dataset-id options)))


(defmethod ig/init-key :planwise.component/facilities
  [_ config]
  (map->FacilitiesService config))
