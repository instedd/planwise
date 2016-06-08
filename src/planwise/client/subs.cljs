(ns planwise.client.subs
  (:require-macros [reagent.ratom :refer [reaction]])
  (:require [re-frame.core :refer [register-sub]]))


;; Subscriptions
;; -------------------------------------------------------

(register-sub
 :current-page
 (fn [db _]
   (reaction (:current-page @db))))

(register-sub
 :map-view
 (fn [db [_ map-id]]
   (reaction (-> @db
                 map-id
                 :map-view))))

(register-sub
 :playground
 (fn [db [_ key]]
   (reaction (get-in @db [:playground key]))))
