(ns planwise.client.scenarios.edit
  (:require [re-frame.core :refer [dispatch subscribe]]
            [reagent.core :as r]
            [planwise.client.utils :as utils]
            [planwise.client.dialog :refer [dialog]]
            [planwise.client.ui.common :as ui]
            [planwise.client.scenarios.db :as db]
            [planwise.client.utils :as utils]
            [planwise.client.components.common2 :as common2]
            [planwise.client.routes :as routes]
            [clojure.string :refer [capitalize] :as str]
            [planwise.client.ui.rmwc :as m]
            [planwise.common :as common]))

;;; Scenario dialogs

(defn rename-scenario-dialog
  []
  (let [dialog-data    (subscribe [:scenarios/rename-dialog-data])
        open?          (subscribe [:scenarios/rename-dialog-open?])
        accept-fn      #(dispatch [:scenarios/accept-rename-dialog])
        cancel-fn      #(dispatch [:scenarios/cancel-dialog])
        update-name-fn (fn [event]
                         (dispatch [:scenarios/save-key
                                    [:rename-dialog :value]
                                    (-> event .-target .-value)]))]
    (fn []
      (let [scenario-name (:value @dialog-data)
            data-valid?   (seq scenario-name)]
        (dialog {:open?       @open?
                 :title       "Rename scenario"
                 :content     [common2/text-field {:label     "Name"
                                                   :value     (str scenario-name)
                                                   :on-change update-name-fn}]
                 :acceptable? data-valid?
                 :accept-fn   accept-fn
                 :cancel-fn   cancel-fn})))))

(defn delete-scenario-dialog
  []
  (let [scenario  (subscribe [:scenarios/current-scenario])
        open?     (subscribe [:scenarios/delete-dialog-open?])
        delete-fn #(dispatch [:scenarios/delete-current-scenario])
        cancel-fn #(dispatch [:scenarios/cancel-dialog])]
    (fn []
      [dialog {:open?     @open?
               :title     (str "Delete " (:name @scenario))
               :content   [:p "Do you want to remove current scenario from project?"]
               :delete-fn delete-fn
               :cancel-fn cancel-fn}])))

(defn- get-investment-from-project-config
  [capacity costs]
  (if (or (zero? capacity) (nil? capacity))
    0
    (let [first     (first costs)
          last      (last costs)
          intervals (map vector costs (drop 1 costs))]
      (cond
        (<= capacity (:capacity first)) (:investment first)
        (>= capacity (:capacity last))  (:investment last)
        :else
        (let [[[a b]] (drop-while (fn [[_ b]] (< (:capacity b) capacity)) intervals)
              m     (/ (- (:investment b) (:investment a)) (- (:capacity b) (:capacity a)))]
          (+ (* m (- capacity (:capacity a))) (:investment a)))))))

(defn- suggest-investment
  [{:keys [capacity action]} {:keys [upgrade-budget building-costs increasing-costs]}]
  (case action
    "upgrade-provider"  (+ (get-investment-from-project-config capacity increasing-costs)
                           upgrade-budget)
    "increase-provider" (get-investment-from-project-config capacity increasing-costs)
    "create-provider"   (get-investment-from-project-config capacity building-costs)))

(defn configured-costs?
  [props]
  (case (get-in props [:change :action])
    "increase-provider" (seq (:increasing-costs props))
    "upgrade-provider"  (and (pos? (:upgrade-budget props))
                             (seq (:increasing-costs props)))
    (seq (:building-costs props))))

(defn new-provider?
  [{:keys [change required-capacity free-capacity]}]
  (and (= (:action change) "create-provider") (nil? free-capacity)))

(defn changeset-dialog-content
  [{:keys [name initial-capacity capacity required-capacity free-capacity available-budget change] :as provider} {:keys [budget? demand-unit capacity-unit] :as props}]
  (let [new?      (new-provider? provider)
        increase? (= (:action change) "increase-provider")
        create?   (= (:action change) "create-provider")
        idle?     (pos? free-capacity)]
    [:div
     ;; Allow name change when creating a new provider
     [common2/text-field (merge {:label "Name"
                                 :value (if create? (:name change) name)}
                                (if create?
                                  {:on-change #(dispatch [:scenarios/save-key [:changeset-dialog :change :name] (-> % .-target .-value)])}
                                  {:read-only true
                                   :focus-extra-class "show-static-text"}))]
     [:div
      (when increase?
        [common2/text-field {:label (str "Current " capacity-unit)
                             :read-only true
                             :value initial-capacity}])
      [common2/numeric-field {:label (if increase? (str (capitalize capacity-unit) " to add") (capitalize capacity-unit))
                              :on-change  #(dispatch [:scenarios/save-key  [:changeset-dialog :change :capacity] %])
                              :value (:capacity change)}]
      ;; Show unsatisfied demand when data is available from an existing provider or
      ;; when creating a provider from a suggestion
      (when (or (some? capacity) (some? required-capacity))
        (let [extra-capacity          (:capacity change)
              total-required-capacity (if idle? (- capacity free-capacity) (+ capacity required-capacity))
              required                (Math/ceil (- total-required-capacity initial-capacity extra-capacity))]
          (cond (not (neg? required)) [:div.inline
                                       [common2/text-field {:label (str "Required " capacity-unit)
                                                            :read-only true
                                                            :value (utils/format-number (Math/abs required))}]
                                       [:p.text-helper (capitalize demand-unit) " without service: " (utils/format-number (* (:project-capacity props) (Math/abs required)))]]
                (neg? required)       [common2/text-field {:label "Free capacity"
                                                           :read-only true
                                                           :value (utils/format-number (Math/abs required))}])))]
     (if budget?
       (let [remaining-budget           (- available-budget (:investment change))
             suggested-cost             (suggest-investment change props)
             show-suggested-cost        (or (configured-costs? props)
                                            (:action-cost provider))]
         [:div
          [common2/numeric-field {:label         "Investment"
                                  :sub-type      :float
                                  :on-change     #(dispatch [:scenarios/save-key [:changeset-dialog :change :investment] %])
                                  :invalid-input (< available-budget (:investment change))
                                  :value         (:investment change)}]
          [common2/numeric-field {:label         "Available budget"
                                  :sub-type      :float
                                  :read-only     true
                                  :value        (if (pos? remaining-budget) remaining-budget 0)}]
          (when show-suggested-cost
            [:p.text-helper
             {:on-click #(dispatch [:scenarios/save-key [:changeset-dialog :change :investment] (min suggested-cost remaining-budget)])}
             "Suggested investment according to project configuration: " (- suggested-cost (:investment change))])]))]))

(def action-labels
  {"create-provider" "Create"
   "upgrade-provider" "Upgrade"
   "increase-provider" "Increase"})

(defn- action->title
  [action]
  (get action-labels action))

(defn changeset-dialog
  [project scenario]
  (let [provider   (subscribe [:scenarios/changeset-dialog])
        open?      (subscribe [:scenarios/changeset-dialog-open?])]
    (fn [{:keys [config] :as project} scenario]
      (let [action        (get-in @provider [:change :action])
            budget        (get-in config [:actions :budget])
            budget?       (common/is-budget (get-in config [:analysis-type]))
            new?          (new-provider? @provider)
            demand-unit   (common/get-demand-unit project)
            capacity-unit (common/get-capacity-unit project)]
        (dialog (merge {:open?       @open?
                        :acceptable? (and (or (not budget?)
                                              ((fnil pos? 0) (get-in @provider [:change :investment])))
                                          ((fnil pos? 0) (get-in @provider [:change :capacity])))
                        :title       (action->title action)
                        :content     (when @open?
                                       (changeset-dialog-content
                                        (merge @provider
                                               (if budget?
                                                 {:available-budget (- budget (:effort scenario))}))
                                        {:project-capacity (get-in config [:providers :capacity])
                                         :upgrade-budget   (get-in config [:actions :upgrade-budget])
                                         :building-costs   (sort-by :capacity (get-in config [:actions :build]))
                                         :increasing-costs (sort-by :capacity (get-in config [:actions :upgrade]))
                                         :budget?          budget?
                                         :demand-unit      demand-unit
                                         :capacity-unit    capacity-unit}))
                        :accept-fn   #(dispatch [:scenarios/accept-changeset-dialog])
                        :cancel-fn   #(dispatch [:scenarios/cancel-dialog])}
                       (when-not new?
                         {:delete-fn #(dispatch [:scenarios/delete-change (:id @provider)])})))))))


;;; "New" actions UI

(defn- new-provider-button
  [{:keys [type on-click disabled]}]
  [:div.border-btn-floating (when (= type :computing)
                              {:class [:border-btn-floating-animated]})
   [m/Fab {:disabled disabled
           :on-click on-click}
    (case type
      :computing "stop"
      :new-unit  "close"
      "add")]])

(defn- is-fetching-suggestions?
  [state]
  (#{:get-suggestions-for-new-provider
     :get-suggestions-for-improvements} state))

(defn- is-adding-change?
  [state]
  (#{:new-provider :new-intervention} state))

(defn create-new-action-component
  [_]
  (let [open?                (r/atom false)
        toggle-menu-fn       #(swap! open? not)
        close-and-dispatch   (fn [event]
                               (fn []
                                 (reset! open? false)
                                 (dispatch event)))
        view-state           (subscribe [:scenarios/view-state])
        cancel-fetch-fn      #(dispatch [:scenarios.new-action/abort-fetching-suggestions])
        cancel-add-change-fn #(dispatch [:scenarios/close-suggestions])]
    (fn [{:keys [provider-unit disabled]}]
      (let [[type on-click]
            (cond
              (is-fetching-suggestions? @view-state) [:computing cancel-fetch-fn]
              (is-adding-change? @view-state)        [:new-unit cancel-add-change-fn]
              :else                                  [:default toggle-menu-fn])]
        [m/MenuAnchor {:className "action-button"}
         [new-provider-button {:type     type
                               :on-click on-click
                               :disabled disabled}]
         [m/Menu {:class (when @open? [:mdc-menu--open])}
          [m/MenuItem
           {:on-click (close-and-dispatch [:scenarios.new-action/simple-create-provider])}
           [m/ListItemGraphic "domain"]
           (str "Add new " provider-unit)]
          [m/MenuItem
           {:on-click (close-and-dispatch [:scenarios.new-provider/fetch-suggested-locations])}
           [m/ListItemGraphic "assistant"]
           (str "Suggest locations for new " provider-unit)]
          [m/MenuItem
           {:on-click (close-and-dispatch [:scenarios.new-action/fetch-suggested-providers-to-improve])}
           [m/ListItemGraphic "arrow_upward"]
           (str "Suggest " provider-unit " to increase capacity")]]]))))
