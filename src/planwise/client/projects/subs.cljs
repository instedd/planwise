(ns planwise.client.projects.subs
  (:require [re-frame.core :as rf]
            [goog.string :as gstring]
            [planwise.client.asdf :as asdf]))

;; ----------------------------------------------------------------------------
;; Projects list subscriptions

(rf/reg-sub
 :projects/view-state
 (fn [db _]
   (get-in db [:projects :view-state])))

(rf/reg-sub
 :projects/search-string
 (fn [db _]
   (get-in db [:projects :search-string])))

(rf/reg-sub
 :projects/list
 (fn [db _]
   (get-in db [:projects :list])))

(defn matches-project?
  [search-string project]
  (gstring/caseInsensitiveContains (:goal project) search-string))

(rf/reg-sub
 :projects/filtered-list
 (fn [_ _]
   [(rf/subscribe [:projects/search-string])
    (rf/subscribe [:projects/list])])
 (fn [[search-string list] _]
   (filterv (partial matches-project? search-string)
            (asdf/value list))))
