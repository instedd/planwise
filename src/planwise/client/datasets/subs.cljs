(ns planwise.client.datasets.subs
  (:require-macros [reagent.ratom :refer [reaction]])
  (:require [re-frame.core :refer [register-sub subscribe]]
            [goog.string :as gstring]))

(register-sub
 :datasets/state
 (fn [db [_]]
   (reaction (get-in @db [:datasets :state]))))

(register-sub
 :datasets/list-state
 (fn [db [_]]
   (reaction (get-in @db [:datasets :state 0]))))

(register-sub
 :datasets/view-state
 (fn [db [_]]
   (reaction (get-in @db [:datasets :state 1]))))

(register-sub
 :datasets/list
 (fn [db [_]]
   (reaction (get-in @db [:datasets :list]))))

(register-sub
 :datasets/search-string
 (fn [db [_]]
   (reaction (get-in @db [:datasets :search-string]))))

(register-sub
 :datasets/filtered-list
 (fn [db [_]]
   (let [search-string (subscribe [:datasets/search-string])
         list (subscribe [:datasets/list])]
     (reaction
      (filterv #(gstring/caseInsensitiveContains (:name %) @search-string) @list)))))

(register-sub
 :datasets/resourcemap
 (fn [db [_]]
   (reaction (get-in @db [:datasets :resourcemap]))))

(register-sub
 :datasets/selected
 (fn [db [_]]
   (reaction (get-in @db [:datasets :selected]))))
