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
 :scenarios/message-error
 (fn [db _]
   (get-in db [:scenarios :current-scenario :raise-error])))

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

(rf/reg-sub
 :scenarios/read-only? :<- [:scenarios/current-scenario]
 (fn [current-scenario [_]]
   (= (:label current-scenario) "initial")))

(rf/reg-sub
 :scenarios/created-providers :<- [:scenarios/current-scenario]
 (fn [current-scenario [_]]
   (keep-indexed (fn [i provider] (when (:action provider) {:provider provider :index i}))
                 (into (:providers current-scenario) (:changeset current-scenario)))))

(rf/reg-sub
 :scenarios.map/selected-provider
 (fn [db _]
   (get-in db [:scenarios :selected-provider])))

(rf/reg-sub
 :scenarios.new-provider/new-suggested-locations
 (fn [db _]
   (get-in db [:scenarios :current-scenario :suggested-locations])))

(rf/reg-sub
 :scenarios.new-provider/suggested-locations
 (fn [_]
   [(rf/subscribe [:scenarios/view-state])
    (rf/subscribe [:scenarios.new-provider/new-suggested-locations])])
 (fn [[view-state suggestions] _]
   (if (= view-state :new-provider)
     suggestions
     nil)))

(rf/reg-sub
 :scenarios.new-provider/computing-best-locations?
 (fn [db _]
   (get-in db [:scenarios :current-scenario :computing-best-locations :state])))

(rf/reg-sub
 :scenarios.new-provider/options :<- [:scenarios/view-state]
 (fn [view-state [_]]
   (= :create-or-suggest-new-provider view-state)))

(rf/reg-sub
 :scenarios/invalid-location-for-provider
 (fn [db _]
   (get-in db [:scenarios :current-scenario :invalid-location-for-provider])))
