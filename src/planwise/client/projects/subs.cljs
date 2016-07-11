(ns planwise.client.projects.subs
  (:require-macros [reagent.ratom :refer [reaction]])
  (:require [re-frame.core :refer [register-sub]]))

(register-sub
 :projects/creating?
 (fn [db [_]]
   (reaction (get-in @db [:projects :creating?]))))

(register-sub
 :projects/current
 (fn [db [_]]
   (reaction (get-in @db [:projects :current]))))

(register-sub
 :projects/facilities
 (fn [db [_ data]]
   (let [facility-data (reaction (get-in @db [:current-project :facilities]))]
     (reaction
      (case data
        :filters (:filters @facility-data)
        :filter-stats (select-keys @facility-data [:count :total]))))))
