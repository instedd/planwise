(ns planwise.client.datasets.subs
  (:require [re-frame.core :as rf]
            [clojure.string :as string]
            [goog.string :as gstring]
            [planwise.client.asdf :as asdf]
            [planwise.client.utils :as utils]))

(rf/reg-sub
 :datasets/state
 (fn [db _]
   (get-in db [:datasets :state])))

(rf/reg-sub
 :datasets/list
 (fn [db _]
   (get-in db [:datasets :list])))

(rf/reg-sub
 :datasets/search-string
 (fn [db _]
   (get-in db [:datasets :search-string])))

(defn matches-dataset?
  [search-string dataset]
  (gstring/caseInsensitiveContains (:name dataset) search-string))

(rf/reg-sub
 :datasets/filtered-list
 (fn [_ _]
   [(rf/subscribe [:datasets/search-string])
    (rf/subscribe [:datasets/list])])
 (fn [[search-string datasets] _]
   (->> datasets
        asdf/value
        (filterv (partial matches-dataset? search-string))
        (sort-by (comp string/lower-case :name)))))

(rf/reg-sub
 :datasets/resourcemap
 (fn [db _]
   (get-in db [:datasets :resourcemap])))

(rf/reg-sub
 :datasets/new-dataset-data
 (fn [db _]
   (get-in db [:datasets :new-dataset-data])))
