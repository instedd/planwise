(ns planwise.client.sources.subs
  (:require [re-frame.core :as rf]
            [planwise.client.asdf :as asdf]
            [planwise.client.utils :as utils]))

(rf/reg-sub
 :sources/list
 (fn [db _]
   (let [sources (get-in db [:sources :list])]
     (when (asdf/should-reload? sources)
       (rf/dispatch [:sources/load]))
     sources)))

(rf/reg-sub
 :sources/list-filtered-by-type-points
 (fn [_]
   (rf/subscribe [:sources/list]))
 (fn [sources]
   (println sources)
   (filter (fn [source] (= (:type source) "points")) (asdf/value sources))))
