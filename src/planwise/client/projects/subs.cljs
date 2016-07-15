(ns planwise.client.projects.subs
  (:require-macros [reagent.ratom :refer [reaction]])
  (:require [re-frame.core :refer [register-sub subscribe]]
            [goog.string :as gstring]))

(register-sub
 :projects/view-state
 (fn [db [_]]
   (reaction (get-in @db [:projects :view-state]))))

(register-sub
 :projects/current
 (fn [db [_]]
   (reaction (get-in @db [:projects :current]))))

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
   (let [facility-data (reaction (get-in @db [:current-project :facilities]))]
     (reaction
      (case data
        :filters (:filters @facility-data)
        :filter-stats (select-keys @facility-data [:count :total]))))))
