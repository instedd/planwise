(ns planwise.client.providers-set.subs
  (:require [re-frame.core :as rf]
            [planwise.client.asdf :as asdf]
            [planwise.client.utils :as utils]))

(rf/reg-sub
 :providers-set/list
 (fn [db _]
   (get-in db [:providers-set :list])))

(rf/reg-sub
 :providers-set/dropdown-options
 (fn [db _]
   (let [list (get-in db [:providers-set :list :value])]
     (mapv (fn [provider-set] (let [{:keys [id name]} provider-set] {:value (str id) :label name})) list))))

(rf/reg-sub
 :providers-set/view-state
 (fn [db _]
   (get-in db [:providers-set :view-state])))

(rf/reg-sub
 :providers-set/last-error
 (fn [db _]
   (get-in db [:providers-set :last-error])))

(rf/reg-sub
 :providers-set/new-provider-set-state
 (fn [db _]
   (get-in db [:providers-set :new-provider-set :state])))

(rf/reg-sub
 :providers-set/new-provider-set-name
 (fn [db _]
   (get-in db [:providers-set :new-provider-set :name])))

(rf/reg-sub
 :providers-set/new-provider-set-js-file
 (fn [db _]
   (get-in db [:providers-set :new-provider-set :js-file])))

(rf/reg-sub
 :providers-set/new-provider-set-coverage
 (fn [db _]
   (get-in db [:providers-set :new-provider-set :coverage])))
