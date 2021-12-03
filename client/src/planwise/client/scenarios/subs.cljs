(ns planwise.client.scenarios.subs
  (:require [re-frame.core :as rf]
            [planwise.client.utils :as utils]
            [planwise.client.scenarios.db :as db]
            [cljs.reader :as edn]))

(rf/reg-sub
 :scenarios/current-scenario
 (fn [db _]
   (get-in db [:scenarios :current-scenario])))

(rf/reg-sub
 :scenarios/view-state
 (fn [db _]
   (get-in db [:scenarios :view-state])))

(rf/reg-sub
 :scenarios/sidebar-expanded?
 :<- [:scenarios/view-state]
 (fn [view-state]
   (= :show-actions-table view-state)))

(rf/reg-sub
 :scenarios/open-dialog
 (fn [db _]
   (get-in db [:scenarios :open-dialog])))

(rf/reg-sub
 :scenarios/error
 :<- [:scenarios/current-scenario]
 (fn [scenario]
   (edn/read-string (:error-message scenario))))

(rf/reg-sub
 :scenarios/rename-dialog-data
 (fn [db _]
   (get-in db [:scenarios :rename-dialog])))

(rf/reg-sub
 :scenarios/rename-dialog-open?
 :<- [:scenarios/open-dialog]
 (fn [open-dialog]
   (= :rename-scenario open-dialog)))

(rf/reg-sub
 :scenarios/delete-dialog-open?
 :<- [:scenarios/open-dialog]
 (fn [open-dialog]
   (= :delete-scenario open-dialog)))

(rf/reg-sub
 :scenarios/changeset-dialog
 (fn [db _]
   (get-in db [:scenarios :changeset-dialog])))

(rf/reg-sub
 :scenarios/changeset-dialog-open?
 :<- [:scenarios/open-dialog]
 (fn [open-dialog]
   (= :scenario-changeset open-dialog)))

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
   (when-let [provider (get-in db [:scenarios :selected-provider])]
     (assoc provider :coverage-geom (get-in db [:scenarios :coverage-cache (:id provider)])))))

(rf/reg-sub
 :scenarios.map/selected-suggestion
 (fn [db _]
   (get-in db [:scenarios :selected-suggestion])))

(rf/reg-sub
 :scenarios.new-provider/new-suggested-locations
 (fn [db _]
   (get-in db [:scenarios :current-scenario :suggested-locations])))

(rf/reg-sub
 :scenarios.new-intervention/suggested-providers
 (fn [db _]
   (get-in db [:scenarios :current-scenario :suggested-providers])))

(defn update-action-with-suggestion
  [provider suggestion]
  (assoc-in provider [:change :capacity] (Math/ceil (:action-capacity suggestion))))

(defn apply-suggestion-to-provider
  [suggestion all-providers changes]
  (let [provider             (utils/find-by-id all-providers (:id suggestion))
        already-in-changeset (utils/find-by-id changes (:id suggestion))
        provider-with-action (if already-in-changeset
                               provider
                               (assoc provider :change (db/new-action provider (if (not (:matches-filters provider)) :upgrade :increase))))]
    (merge
     (update-action-with-suggestion provider-with-action suggestion)
     suggestion)))

(rf/reg-sub
 :scenarios.new-provider/suggested-locations
 (fn [_]
   [(rf/subscribe [:scenarios/view-state])
    (rf/subscribe [:scenarios.new-provider/new-suggested-locations])
    (rf/subscribe [:scenarios.new-intervention/suggested-providers])
    (rf/subscribe [:scenarios/all-providers])
    (rf/subscribe [:scenarios/providers-from-changeset])])
 (fn [[view-state suggested-locations suggested-providers all-providers providers-from-changeset] _]
   (case view-state
     :new-provider     suggested-locations
     :new-intervention (map
                        #(apply-suggestion-to-provider % all-providers providers-from-changeset)
                        suggested-providers)
     nil)))


(rf/reg-sub
 :scenarios.new-provider/computing-best-locations?
 (fn [db _]
   (get-in db [:scenarios :current-scenario :computing-best-locations :state])))


(rf/reg-sub
 :scenarios.new-intervention/computing-best-improvements?
 (fn [db _]
   (get-in db [:scenarios :current-scenario :computing-best-improvements :state])))

(defn- update-capacity-and-demand
  [{:keys [capacity] :as provider} providers-data]
  (merge provider
         (select-keys
          (utils/find-by-id providers-data (:id provider))
          [:capacity :satisfied-demand :unsatisfied-demand :free-capacity :required-capacity :reachable-demand])
         {:initial-capacity capacity}))

(defn- apply-change
  [providers change]
  (if (= (:action change) "create-provider")
    (conj providers (db/new-provider-from-change change))
    (utils/update-by-id providers (:id change) assoc :change change)))

(rf/reg-sub
 :scenarios/all-providers
 :<- [:scenarios/current-scenario]
 (fn [{:keys [providers disabled-providers changeset providers-data] :as scenario} _]
   (let [providers' (concat (map #(assoc % :matches-filters true)
                                 providers)
                            (map #(assoc % :matches-filters false :capacity 0)
                                 disabled-providers))]
     (map
      #(update-capacity-and-demand % providers-data)
      (reduce apply-change providers' changeset)))))

(rf/reg-sub
 :scenarios/providers-from-changeset
 (fn [_]
   [(rf/subscribe [:scenarios/all-providers])
    (rf/subscribe [:scenarios/current-scenario])])
 (fn [[all-providers {:keys [changeset]}] _]
   (map #(utils/find-by-id all-providers (:id %)) changeset)))

(rf/reg-sub
 :scenarios.current/source-demand
 (fn [db]
   (get-in db [:scenarios :current-scenario :source-demand] 0)))

(rf/reg-sub
 :scenarios.current/population-under-coverage
 (fn [db]
   (get-in db [:scenarios :current-scenario :population-under-coverage] 0)))

(rf/reg-sub
 :scenarios/sort-column
 (fn [db _]
   (get-in db [:scenarios :sort-column])))

(rf/reg-sub
 :scenarios/sort-order
 (fn [db _]
   (get-in db [:scenarios :sort-order])))

(rf/reg-sub
 :scenarios/searching-providers?
 :<- [:scenarios/view-state]
 (fn [view-state]
   (= :search-providers view-state)))

(rf/reg-sub
 :scenarios/search-providers-matches
 (fn [db _]
   (get-in db [:scenarios :providers-search :matches])))
