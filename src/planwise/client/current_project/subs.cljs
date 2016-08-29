(ns planwise.client.current-project.subs
  (:require-macros [reagent.ratom :refer [reaction]])
  (:require [re-frame.core :refer [register-sub subscribe]]
            [planwise.client.current-project.db :as db]
            [planwise.client.mapping :as mapping]))


;; ----------------------------------------------------------------------------
;; Current project subscriptions

(register-sub
 :current-project/loaded?
 (fn [db [_]]
   (reaction (= (get-in @db [:current-project :project-data :id])
                (js/parseInt (get-in @db [:page-params :id]))))))

(register-sub
 :current-project/current-data
 (fn [db [_]]
   (reaction (get-in @db [:current-project :project-data]))))

(register-sub
 :current-project/filter-definition
 (fn [db [_ filter]]
   (reaction (get-in @db [:current-project :filter-definitions filter]))))

(register-sub
 :current-project/facilities
 (fn [db [_ data]]
   (let [facility-data (reaction (get-in @db [:current-project :facilities]))]
     (reaction
      (case data
        :filters (:filters @facility-data)
        :isochrones (:isochrones @facility-data)
        :filter-stats (select-keys @facility-data [:count :total])
        :facilities (:list @facility-data))))))

(register-sub
 :current-project/facilities-by-type
 (fn [db [_ data]]
   (let [facilities (reaction (get-in @db [:current-project :facilities :list]))
         types      (subscribe [:current-project/filter-definition :facility-type])]
     (reaction
       (->> @facilities
         (group-by :type-id)
         (map (fn [[type-id fs]]
                (let [type (->> @types
                              (filter #(= type-id (:value %)))
                              (first))]
                  [type fs])))
         (sort-by (fn [[type fs]]
                    (count fs)))
         (reverse))))))

(register-sub
 :current-project/facilities-criteria
 (fn [db [_]]
   (reaction (db/facilities-criteria (get-in @db [:current-project])))))

(register-sub
 :current-project/transport-time
 (fn [db [_]]
   (reaction (get-in @db [:current-project :transport :time]))))

(register-sub
 :current-project/demand-map-key
 (fn [db [_]]
   (reaction (get-in @db [:current-project :demand-map-key]))))

(register-sub
 :current-project/map-state
 (fn [db [_]]
   (reaction (get-in @db [:current-project :map-state :current]))))

(register-sub
 :current-project/map-view
 (fn [db [_ field]]
   (let [map-view (reaction (get-in @db [:current-project :map-view]))
         current-region-id (reaction (get-in @db [:current-project :project-data :region-id]))
         current-region-max-population (reaction (get-in @db [:current-project :project-data :region-max-population]))
         current-region (reaction (get-in @db [:regions @current-region-id]))]
     (reaction
       (case field
         :position (or
                     (:position @map-view)
                     (mapping/bbox-center (:bbox @current-region))
                     (:position db/initial-position-and-zoom))
         :zoom (or
                 (:zoom @map-view)
                 (+ 4 (:admin-level @current-region))
                 (:zoom db/initial-position-and-zoom))
         :bbox (:bbox @current-region)
         :legend-max @current-region-max-population)))))

(register-sub
 :current-project/map-geojson
 (fn [db [_]]
   (let [current-region-id (reaction (get-in @db [:current-project :project-data :region-id]))]
     (reaction (get-in @db [:regions @current-region-id :geojson])))))
