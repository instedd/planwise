(ns planwise.client.scenarios.subs
  (:require [re-frame.core :as rf]
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

;TODO necessary indexed changes?
(rf/reg-sub
 :scenarios/providers-from-changeset :<- [:scenarios/current-scenario]
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
   (= :show-options-to-create-provider view-state)))

(defn new-provider-from-change
  [change index]
  {:provider-id    (:provider-id change)
   :name           (str "New Provider " index)
   :location       (:location change)
   :matches-filter true
   :change         change})

(defn update-by-id
  [providers id update-fn]
  (map (fn [p] (if (= id (:provider-id p))
                 (update-fn p)
                 p))
       providers))

(defn apply-change
  [providers [index change]]
  (if (= (:action change) "create-provider")
    (conj providers (new-provider-from-change change index))
    (update-by-id providers (:provider-id change) #(assoc % :change change))))

(rf/reg-sub
 :scenarios/all-providers :<- [:scenarios/current-scenario]
 (fn [{:keys [providers disabled-providers changeset] :as scenario} _]
   (let [providers' (concat (map #(assoc % :matches-filters true)
                                 providers)
                            (map #(assoc % :matches-filters false)
                                 disabled-providers))]
     (reduce apply-change providers' (map-indexed vector changeset)))))
