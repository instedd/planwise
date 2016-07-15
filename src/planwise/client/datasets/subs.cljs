(ns planwise.client.datasets.subs
  (:require-macros [reagent.ratom :refer [reaction]])
  (:require [re-frame.core :refer [register-sub subscribe]]))

(register-sub
 :datasets/initialised?
 (fn [db [_]]
   (reaction (get-in @db [:datasets :initialised?]))))

(register-sub
 :datasets/facility-count
 (fn [db [_]]
   (reaction (get-in @db [:datasets :facility-count]))))

(register-sub
 :datasets/resourcemap
 (fn [db [_]]
   (reaction (get-in @db [:datasets :resourcemap]))))
