(ns planwise.client.scenarios.subs
  (:require [re-frame.core :as rf]
            [planwise.client.utils :as utils]
            [planwise.client.scenarios.db :as db]
            [cljs.reader]))

(rf/reg-sub
 :scenarios/current-scenario
 (fn [db _]
   (get-in db [:scenarios :current-scenario])))

(rf/reg-sub
 :scenarios/view-state
 (fn [db _]
   (get-in db [:scenarios :view-state])))

(rf/reg-sub
 :scenarios/error
 (fn [db _]
   (cljs.reader/read-string (get-in db [:scenarios :current-scenario :error-message]))))

(rf/reg-sub
 :scenarios/rename-dialog
 (fn [db _]
   (get-in db [:scenarios :rename-dialog])))

(rf/reg-sub
 :scenarios/changeset-dialog
 (fn [db _]
   (get-in db [:scenarios :changeset-dialog])))

(rf/reg-sub
 :scenarios/list
 (fn [db _]
   (get-in db [:scenarios :list])))

(rf/reg-sub
 :scenarios/read-only? :<- [:scenarios/current-scenario]
 (fn [current-scenario [_]]
   (= (:label current-scenario) "initial")))

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
   (= :show-options-to-create-provider view-state)))

(defn update-capacity-and-demand
  [{:keys [capacity] :as provider} providers-data]
  (merge provider
         (select-keys
          (utils/find-by-id providers-data (:id provider))
          [:capacity :satisfied-demand :unsatisfied-demand :free-capacity :required-capacity])
         {:initial-capacity capacity}))

(defn apply-change
  [providers [index change]]
  (if (= (:action change) "create-provider")
    (conj providers (db/new-provider-from-change change))
    (utils/update-by-id providers (:id change) assoc :change change)))

(rf/reg-sub
 :scenarios/all-providers :<- [:scenarios/current-scenario]
 (fn [{:keys [providers disabled-providers changeset providers-data] :as scenario} _]
   (let [providers' (concat (map #(assoc % :matches-filters true)
                                 providers)
                            (map #(assoc % :matches-filters false :capacity 0)
                                 disabled-providers))]
     (map
      #(update-capacity-and-demand % providers-data)
      (reduce apply-change providers' (map-indexed vector changeset))))))

(rf/reg-sub
 :scenarios/providers-from-changeset
 (fn [_]
   [(rf/subscribe [:scenarios/all-providers])
    (rf/subscribe [:scenarios/current-scenario])])
 (fn [[all-providers {:keys [changeset]}] _]
   (map #(utils/find-by-id all-providers (:id %)) changeset)))
