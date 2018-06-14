(ns planwise.client.sources.subs
  (:require [re-frame.core :as rf]
            [planwise.client.asdf :as asdf]
            [clojure.string :as str]))

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
   (filter (fn [source] (= (:type source) "points")) (asdf/value sources))))

(rf/reg-sub
 :sources.new/data
 (fn [db _]
   (get-in db [:sources :new])))

(rf/reg-sub
 :sources.new/valid?
 (fn [_]
   (rf/subscribe [:sources.new/data]))
 (fn [new-source]
   (let [name (:name new-source)
         csv-file (:csv-file new-source)]
     (not (or (str/blank? name)
              (nil? csv-file))))))
