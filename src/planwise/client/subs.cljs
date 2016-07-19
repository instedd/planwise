(ns planwise.client.subs
  (:require-macros [reagent.ratom :refer [reaction]])
  (:require [re-frame.core :refer [register-sub]]
            [planwise.client.projects.subs]
            [planwise.client.regions.subs]
            [planwise.client.playground.subs]))


;; Subscriptions
;; -------------------------------------------------------

(register-sub
 :current-page
 (fn [db _]
   (reaction (:current-page @db))))

(register-sub
 :page-params
 (fn [db _]
   (reaction (:page-params @db))))

(register-sub
 :filter-definition
 (fn [db [_ filter]]
   (let [options (reaction (get-in @db [:filter-definitions filter]))]
     (if (map? (first @options))
       (reaction @options)
       (reaction (map #(assoc {} :value % :label %) @options))))))
