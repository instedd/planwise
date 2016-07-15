(ns planwise.client.regions.handlers
  (:require [re-frame.core :refer [register-handler path dispatch]]
            [accountant.core :as accountant]
            [planwise.client.routes :as routes]
            [planwise.client.regions.api :as api]))

(def in-regions (path [:regions]))

(register-handler
 :regions/load-regions-with-geo
 in-regions
 (fn [db [_ region-ids]]
   (api/load-regions-with-geo region-ids :regions/regions-with-geo-loaded)
   db))

(register-handler
 :regions/regions-with-geo-loaded
 in-regions
  (fn [db [_ regions-data]]
    (reduce
      (fn [db {id :id :as region}]
        (assoc db id region))
      db regions-data)))
