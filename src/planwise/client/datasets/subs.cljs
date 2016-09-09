(ns planwise.client.datasets.subs
  (:require-macros [reagent.ratom :refer [reaction]])
  (:require [re-frame.core :refer [register-sub subscribe]]
            [clojure.string :as string]
            [goog.string :as gstring]
            [planwise.client.asdf :as asdf]
            [planwise.client.utils :as utils]))

(register-sub
 :datasets/state
 (fn [db [_]]
   (reaction (get-in @db [:datasets :state]))))

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
         datasets (subscribe [:datasets/list])]
     (reaction
       (->> @datasets
         (asdf/value)
         (filterv #(gstring/caseInsensitiveContains (:name %) @search-string))
         (sort-by (comp string/lower-case :name)))))))

(register-sub
 :datasets/resourcemap
 (fn [db [_]]
   (reaction (get-in @db [:datasets :resourcemap]))))

(register-sub
 :datasets/new-dataset-data
 (fn [db [_]]
   (reaction (get-in @db [:datasets :new-dataset-data]))))
