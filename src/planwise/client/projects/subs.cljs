(ns planwise.client.projects.subs
  (:require-macros [reagent.ratom :refer [reaction]])
  (:require [re-frame.core :refer [register-sub]]))

(register-sub
 :projects/creating?
 (fn [db [_]]
   (reaction (get-in @db [:projects :creating?]))))
