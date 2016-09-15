(ns planwise.client.regions.handlers
  (:require [re-frame.core :refer [register-handler path dispatch]]
            [accountant.core :as accountant]
            [planwise.client.routes :as routes]
            [planwise.client.regions.api :as api]))

(def in-regions (path [:regions]))

(register-handler
 :regions/load-regions
 in-regions
 (fn [db [_]]
   (api/load-regions :regions/regions-loaded)
   db))

(register-handler
 :regions/load-regions-with-preview
 in-regions
 (fn [db [_ region-ids]]
   (let [missing-region-ids (remove #(get-in db [% :preview-geojson]) region-ids)]
     (api/load-regions-with-preview missing-region-ids :regions/regions-loaded))
   db))

(register-handler
 :regions/load-regions-with-geo
 in-regions
 (fn [db [_ region-ids]]
   (let [missing-region-ids (->> region-ids
                              (filter some?)
                              (remove #(get-in db [% :geojson])))]
     (api/load-regions-with-geo missing-region-ids :regions/regions-loaded))
   db))

(register-handler
 :regions/regions-loaded
 in-regions
  (fn [db [_ regions-data]]
    (reduce
      (fn [db {id :id :as region}]
        (update db id #(merge % region)))
      db regions-data)))
