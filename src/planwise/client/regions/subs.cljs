(ns planwise.client.regions.subs
  (:require-macros [reagent.ratom :refer [reaction]])
  (:require [re-frame.core :refer [register-sub subscribe]]))

(register-sub
 :regions/geojson
 (fn [db [_ region-id]]
   (reaction (get-in @db [:regions region-id :geojson]))))
