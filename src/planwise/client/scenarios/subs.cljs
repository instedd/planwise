(ns planwise.client.scenarios.subs
  (:require [re-frame.core :as rf]))

(rf/reg-sub
 :scenarios/current-scenario
 (fn [db _]
   (get-in db [:scenarios :current-scenario])))

