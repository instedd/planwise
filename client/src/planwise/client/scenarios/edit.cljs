(ns planwise.client.scenarios.edit
  (:require [re-frame.core :refer [dispatch subscribe]]
            [reagent.core :as r]
            [planwise.client.utils :as utils]
            [planwise.client.ui.dialog :refer [dialog]]
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
                 :class       "narrow"
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
               :class     "narrow"
               :content   [:p.dialog-prompt
                           "Do you want to remove current scenario from project?"
                           [:br]
                           [:strong "This action cannot be undone."]]
               :delete-fn delete-fn
               :cancel-fn cancel-fn}])))


;;; Changeset dialogs

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
    "create-provider"   (get-investment-from-project-config capacity building-costs)
    nil))

(defn- provider-name-input
  [{:keys [provider update-change-fn]}]
  (let [name (get-in provider [:change :name])]
    [:div
     [common2/text-field {:label      "Name"
                          :value      name
                          :class      "full-width"
                          :auto-focus true
                          :on-change  #(update-change-fn :name (-> % .-target .-value))}]]))

(defn- provider-name-render
  [{:keys [provider]}]
  [:div.provider-name
   [m/Icon "domain"]
   [:span (:name provider)]])

(defn- capacity-input-section
  [{:keys [provider update-change-fn capacity-unit demand-unit project-capacity]}]
  (let [{:keys [change initial-capacity satisfied-demand unsatisfied-demand]} provider

        demand-to-cover    (+ (or satisfied-demand 0) (or unsatisfied-demand 0))
        ideal-capacity     (Math/ceil (- (/ demand-to-cover project-capacity) (or initial-capacity 0)))
        formatted-ideal    (utils/format-units ideal-capacity capacity-unit)
        formatted-demand   (utils/format-units demand-to-cover demand-unit)
        creating-provider? (= "create-provider" (:action change))
        accept-hint-fn     #(update-change-fn :capacity ideal-capacity)]
    [:div
     [common2/numeric-field {:label     (if creating-provider?
                                          "Capacity to build"
                                          "Capacity to add")
                             :value     (:capacity change)
                             :suffix    capacity-unit
                             :on-change (partial update-change-fn :capacity)}]
     (cond
       (and creating-provider? (pos? ideal-capacity))
       [:p.text-helper.change-hint
        {:on-click accept-hint-fn}
        "A total of " formatted-ideal " required to cover " formatted-demand "."]

       (pos? ideal-capacity)
       [:p.text-helper.change-hint
        {:on-click accept-hint-fn}
        "Adding " formatted-ideal " is required to cover " formatted-demand "."]

       (not creating-provider?)
       [:p.text-helper
        "This provider already satisfies the required demand."])]))

(defn- existing-capacity-section
  [{:keys [provider capacity-unit]}]
  (let [{:keys [initial-capacity]} provider]
    [:div.fixed-content
     [:div.label "Existing capacity"]
     [:div.value (utils/format-units initial-capacity capacity-unit)]]))

(defn- investment-section
  [{:keys [provider update-change-fn available-budget budget?] :as props}]
  (let [change (:change provider)]
    (if budget?
      [:div
       [common2/numeric-field {:label     "Investment"
                               :value     (:investment change)
                               :sub-type  :float
                               :prefix    common/currency-symbol
                               :on-change (partial update-change-fn :investment)}]
       (let [suggested-investment (suggest-investment change props)]
         [:p.text-helper.change-hint
          {:on-click #(update-change-fn :investment suggested-investment)}
          "Cost from configuration: "
          (utils/format-currency suggested-investment)
          [:br]
          "Available budget: "
          (utils/format-currency available-budget)])]
      [:div])))

(defn- changeset-dialog-content
  [props]
  (let [update-change-fn (fn [field value]
                           (dispatch [:scenarios/save-key [:changeset-dialog :provider :change field] value]))
        props            (merge props {:update-change-fn update-change-fn})
        action           (get-in props [:provider :change :action])
        creating?        (= "create-provider" action)
        increasing?      (= "increase-provider" action)]
    [:<>
     (if creating?
       [provider-name-input props]
       [provider-name-render props])
     [:div.columns
      (when increasing? [existing-capacity-section props])
      [capacity-input-section props]
      [investment-section props]]]))

(defn- change-dialog-title
  [project {:keys [change] :as provider}]
  (let [capacity-unit (common/get-capacity-unit project)
        [title subtitle]
        (case (:action change)
          "create-provider"   ["Add new provider"
                               "Build a new provider at the location"]
          "upgrade-provider"  ["Upgrade provider"
                               (str "This provider will need to be upgraded before adding new "
                                    capacity-unit)]
          "increase-provider" ["Increase capacity"
                               (str "Add new " capacity-unit " to the existing provider")]
          nil)]
    [:<>
     title
     [:div.subtitle subtitle]]))

(defn- change-valid?
  [project change]
  (and (or (not (common/project-has-budget? project))
           (pos? (or (:investment change) 0)))
       (pos? (or (:capacity change) 0))))

(defn changeset-dialog
  [project scenario]
  (let [dialog-data (subscribe [:scenarios/changeset-dialog])
        open?       (subscribe [:scenarios/changeset-dialog-open?])]
    (fn [{:keys [config] :as project} scenario]
      (let [provider         (:provider @dialog-data)
            new-change?      (:new-change? @dialog-data)
            available-budget (:available-budget @dialog-data)
            budget?          (common/project-has-budget? project)
            demand-unit      (common/get-demand-unit project)
            capacity-unit    (common/get-capacity-unit project)]
        (dialog {:open?       @open?
                 :acceptable? (change-valid? project (:change provider))
                 :title       (change-dialog-title project provider)
                 :class       "changeset-dialog"
                 :content     (when @open?
                                (changeset-dialog-content
                                 {:provider         provider
                                  :project-capacity (get-in config [:providers :capacity])
                                  :upgrade-budget   (get-in config [:actions :upgrade-budget])
                                  :building-costs   (sort-by :capacity (get-in config [:actions :build]))
                                  :increasing-costs (sort-by :capacity (get-in config [:actions :upgrade]))
                                  :budget?          budget?
                                  :available-budget available-budget
                                  :demand-unit      demand-unit
                                  :capacity-unit    capacity-unit}))
                 :accept-fn   #(dispatch [:scenarios/accept-changeset-dialog])
                 :cancel-fn   #(dispatch [:scenarios/cancel-dialog])
                 :delete-fn   (when-not new-change?
                                #(dispatch [:scenarios/delete-change (:id provider)]))})))))


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
           (str "Suggest " provider-unit " to improve")]]]))))
