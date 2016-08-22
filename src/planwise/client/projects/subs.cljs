(ns planwise.client.projects.subs
  (:require-macros [reagent.ratom :refer [reaction]])
  (:require [re-frame.core :refer [register-sub subscribe]]
            [goog.string :as gstring]
            [planwise.client.projects.db :as db]
            [planwise.client.mapping :as mapping]))

(register-sub
 :projects/view-state
 (fn [db [_]]
   (reaction (get-in @db [:projects :view-state]))))

(register-sub
 :projects/current-data
 (fn [db [_]]
   (reaction (get-in @db [:projects :current :project-data]))))

(register-sub
 :projects/search-string
 (fn [db [_]]
   (reaction (get-in @db [:projects :search-string]))))

(register-sub
 :projects/list
 (fn [db [_]]
   (reaction (get-in @db [:projects :list]))))

(register-sub
 :projects/filtered-list
 (fn [db [_]]
   (let [search-string (subscribe [:projects/search-string])
         list (subscribe [:projects/list])]
     (reaction
       (filterv #(gstring/caseInsensitiveContains (:goal %) @search-string) @list)))))

(register-sub
 :projects/facilities
 (fn [db [_ data]]
   (let [facility-data (reaction (get-in @db [:projects :current :facilities]))]
     (reaction
      (case data
        :filters (:filters @facility-data)
        :isochrones (:isochrones @facility-data)
        :filter-stats (select-keys @facility-data [:count :total])
        :facilities (:list @facility-data))))))

(register-sub
 :projects/facilities-criteria
 (fn [db [_]]
   (reaction (db/facilities-criteria (get-in @db [:projects :current])))))

(register-sub
 :projects/transport-time
 (fn [db [_]]
   (reaction (get-in @db [:projects :current :transport :time]))))

(register-sub
 :projects/demand-map-key
 (fn [db [_]]
   (reaction (get-in @db [:projects :current :demand-map-key]))))

(register-sub
 :projects/map-state
 (fn [db [_]]
   (reaction (get-in @db [:projects :current :map-state :current]))))

(register-sub
 :projects/map-view
 (fn [db [_ field]]
   (let [map-view (reaction (get-in @db [:projects :current :map-view]))
         current-region-id (reaction (get-in @db [:projects :current :project-data :region-id]))
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
         :bbox (:bbox @current-region))))))

(register-sub
 :projects/map-geojson
 (fn [db [_]]
   (let [current-region-id (reaction (get-in @db [:projects :current :project-data :region-id]))]
     (reaction (get-in @db [:regions @current-region-id :geojson])))))
