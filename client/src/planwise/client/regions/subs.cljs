(ns planwise.client.regions.subs
  (:require [re-frame.core :as rf]))

(rf/reg-sub
 :regions/list
 (fn [db _]
   (sort-by :name (vals (:regions db)))))

(rf/reg-sub
 :regions/preview-geojson
 (fn [db [_ region-id]]
   (get-in db [:regions region-id :preview-geojson])))

(rf/reg-sub
 :regions/geojson
 (fn [db [_ region-id]]
   (get-in db [:regions region-id :geojson])))

