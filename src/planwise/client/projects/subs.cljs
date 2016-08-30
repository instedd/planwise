(ns planwise.client.projects.subs
  (:require-macros [reagent.ratom :refer [reaction]])
  (:require [re-frame.core :refer [register-sub subscribe]]
            [goog.string :as gstring]
            [planwise.client.asdf :as asdf]))

;; ----------------------------------------------------------------------------
;; Projects list subscriptions

(register-sub
 :projects/view-state
 (fn [db [_]]
   (reaction (get-in @db [:projects :view-state]))))

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
      (filterv #(gstring/caseInsensitiveContains (:goal %) @search-string) (asdf/value @list))))))
