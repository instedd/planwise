(ns planwise.client.subs
  (:require-macros [reagent.ratom :refer [reaction]])
  (:require [re-frame.core :refer [register-sub]]
            [planwise.client.playground.subs]))


;; Subscriptions
;; -------------------------------------------------------

(register-sub
 :current-page
 (fn [db _]
   (reaction (:current-page @db))))

