(ns planwise.client.projects.subs
  (:require-macros [reagent.ratom :refer [reaction]])
  (:require [re-frame.core :refer [register-sub]]))

(register-sub
 :projects/view-state
 (fn [db [_]]
   (reaction (get-in @db [:projects :view-state]))))

(register-sub
 :projects/current
 (fn [db [_]]
   (reaction (get-in @db [:projects :current]))))

(register-sub
 :projects/facilities
 (fn [db [_ data]]
   (let [facility-data (reaction (get-in @db [:current-project :facilities]))
         map-view (reaction (get-in @db [:current-project :map-view]))]
     (reaction
      (case data
        :filters (:filters @facility-data)
        :filter-stats (select-keys @facility-data [:count :total])
        :facilities (:facilities @facility-data)
        :map-view @map-view)))))
