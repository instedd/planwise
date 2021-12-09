(ns planwise.client.scenarios.subs
  (:require [re-frame.core :as rf]
            [planwise.client.utils :as utils]
            [planwise.client.scenarios.db :as db]
            [cljs.reader :as edn]))

(rf/reg-sub
 :scenarios/list
 (fn [db _]
   (get-in db [:scenarios :list])))

(rf/reg-sub
 :scenarios/current-scenario
 (fn [db _]
   (get-in db [:scenarios :current-scenario])))

(rf/reg-sub
 :scenarios/initial-scenario?
 :<- [:scenarios/current-scenario]
 (fn [current-scenario [_]]
   (db/initial-scenario? current-scenario)))

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
 :scenarios/can-expand-sidebar?
 :<- [:scenarios/view-state]
 :<- [:scenarios/initial-scenario?]
 (fn [[view-state initial-scenario?]]
   (and (not initial-scenario?)
        (#{:current-scenario :show-actions-table} view-state))))

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

(rf/reg-sub
 :scenarios/all-providers
 :<- [:scenarios/current-scenario]
 (fn [scenario _]
   (db/all-providers scenario)))

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

(rf/reg-sub
 :scenarios/search-matching-ids
 :<- [:scenarios/search-providers-matches]
 (fn [matches]
   (set (map :id matches))))

(defn bbox-from-location
  [{:keys [lat lon]}]
  [[lat lon] [lat lon]])

(rf/reg-sub
 :scenarios.map/search-matches-bbox
 :<- [:scenarios/search-providers-matches]
 (fn [matches]
   (cond
     (empty? matches)
     nil

     (= 1 (count matches))
     (bbox-from-location (:location (first matches)))

     :else
     (reduce (fn [[[s w] [n e]] {{:keys [lat lon]} :location}]
               [[(min s lat) (min w lon)] [(max n lat) (max e lon)]])
             (bbox-from-location (:location (first matches)))
             (rest matches)))))
