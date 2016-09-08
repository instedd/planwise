(ns planwise.client.regions.subs
  (:require-macros [reagent.ratom :refer [reaction]])
  (:require [re-frame.core :refer [register-sub subscribe]]))

(register-sub
 :regions/list
 (fn [db [_]]
   (reaction (sort-by :name (vals (:regions @db))))))

(register-sub
 :regions/preview-geojson
 (fn [db [_ region-id]]
   (reaction (get-in @db [:regions region-id :preview-geojson]))))

(register-sub
 :regions/geojson
 (fn [db [_ region-id]]
   (reaction (get-in @db [:regions region-id :geojson]))))

(register-sub
 :regions/bbox
 (fn [db [_ region-id]]
   (reaction (get-in @db [:regions region-id :bbox]))))

(register-sub
 :regions/admin-level
 (fn [db [_ region-id]]
   (reaction (get-in @db [:regions region-id :admin-level]))))
