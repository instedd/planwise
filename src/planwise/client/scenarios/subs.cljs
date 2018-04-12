(ns planwise.client.scenarios.subs
  (:require [re-frame.core :as rf]))

(rf/reg-sub
 :scenarios/current-scenario
 (fn [db _]
   (get-in db [:scenarios :current-scenario])))

(rf/reg-sub
 :scenarios/view-state
 (fn [db _]
   (get-in db [:scenarios :view-state])))

(rf/reg-sub
 :scenarios/rename-dialog
 (fn [db _]
   (get-in db [:scenarios :rename-dialog])))

(rf/reg-sub
 :scenarios/changeset-dialog
 (fn [db _]
   (get-in db [:scenarios :changeset-dialog])))

(rf/reg-sub
 :scenarios/changeset-index
 (fn [db _]
   (get-in db [:scenarios :view-state-params :changeset-index])))

(rf/reg-sub
 :scenarios/list
 (fn [db _]
   (get-in db [:scenarios :list])))
