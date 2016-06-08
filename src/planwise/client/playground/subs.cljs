(ns planwise.client.playground.subs
  (:require-macros [reagent.ratom :refer [reaction]])
  (:require [re-frame.core :refer [register-sub]]))

(register-sub
 :playground
 (fn [db [_]]
   (reaction (:playground @db))))
