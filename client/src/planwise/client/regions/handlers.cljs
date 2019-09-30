(ns planwise.client.regions.handlers
  (:require [re-frame.core :as rf]
            [planwise.client.regions.api :as api]))

(def in-regions (rf/path [:regions]))

(rf/reg-event-fx
 :regions/load-regions
 (fn [_ _]
   {:api (assoc api/load-regions
                :on-success [:regions/regions-loaded])}))

(defn ids-for-regions-without
  [field regions ids]
  (->> ids
       (filter some?)
       (remove (fn [id] (get-in regions [id field])))
       seq))

(rf/reg-event-fx
 :regions/load-regions-with-preview
 in-regions
 (fn [{:keys [db]} [_ region-ids]]
   (when-some [missing-region-ids (ids-for-regions-without :preview-geojson db region-ids)]
     {:api (assoc (api/load-regions-with-preview missing-region-ids)
                  :on-success [:regions/regions-loaded])})))

(rf/reg-event-fx
 :regions/load-regions-with-geo
 in-regions
 (fn [{:keys [db]} [_ region-ids]]
   (when-some [missing-region-ids (ids-for-regions-without :geojson db region-ids)]
     {:api (assoc (api/load-regions-with-geo missing-region-ids)
                  :on-success [:regions/regions-loaded])})))

(rf/reg-event-db
 :regions/regions-loaded
 in-regions
 (fn [db [_ regions-data]]
   (reduce
    (fn [db {id :id :as region}]
      (update db id #(merge % region)))
    db regions-data)))
