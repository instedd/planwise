(ns planwise.client.scenarios.edit
  (:require [re-frame.core :refer [dispatch subscribe]]
            [reagent.core :as r]
            [planwise.client.asdf :as asdf]
            [planwise.client.utils :as utils]
            [planwise.client.dialog :refer [dialog]]
            [planwise.client.ui.common :as ui]
            [planwise.client.scenarios.db :as db]
            [planwise.client.utils :as utils]
            [planwise.client.components.common2 :as common2]
            [planwise.client.routes :as routes]
            [clojure.string :as str]
            [planwise.client.ui.rmwc :as m]))

(defn rename-scenario-dialog
  []
  (let [rename-dialog (subscribe [:scenarios/rename-dialog])
        view-state    (subscribe [:scenarios/view-state])]
    (fn []
      (dialog {:open?       (= @view-state :rename-dialog)
               :title       "Rename scenario"
               :content     [common2/text-field {:label     "Name"
                                                 :value     (str (:value @rename-dialog))
                                                 :on-change #(dispatch [:scenarios/save-key
                                                                        [:rename-dialog :value] (-> % .-target .-value)])}]
               :acceptable? (seq (:value @rename-dialog))
               :accept-fn   #(dispatch [:scenarios/accept-rename-dialog])
               :cancel-fn   #(dispatch [:scenarios/cancel-dialog])}))))

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

(defn changeset-dialog-content
  [{:keys [name initial-capacity capacity required-capacity free-capacity available-budget change] :as provider} props]
  (let [new?      (and (= (:action change) "create-provider") (nil? required-capacity) (nil? free-capacity))
        increase? (=  (:action change) "increase-provider")
        idle?     (pos? free-capacity)]
    [:div
     [common2/text-field  {:label "Name"
                           :read-only true
                           :focus-extra-class "show-static-text"
                           :value name}]
     [:div
      (when increase?
        [common2/text-field {:label "Original capacity"
                             :read-only true
                             :value initial-capacity}])
      [common2/numeric-text-field {:type "number"
                                   :label (if increase? "Extra capacity" "Capacity")
                                   :on-change  #(dispatch [:scenarios/save-key  [:changeset-dialog :change :capacity] %])
                                   :value (or (:capacity change) "")}]
      (when-not new?
        (let [extra-capacity          (:capacity change)
              total-required-capacity (if idle? (- capacity free-capacity) (+ capacity required-capacity))
              required                (Math/ceil (- total-required-capacity initial-capacity extra-capacity))]
          (cond (not (neg? required)) [:div.inline
                                       [common2/text-field {:label "Required capacity"
                                                            :read-only true
                                                            :value (utils/format-number (Math/abs required))}]
                                       [:p.text-helper "Unsatisfied demand: " (utils/format-number (* (:project-capacity props) (Math/abs required)))]]
                (neg? required)       [common2/text-field {:label "Free capacity"
                                                           :read-only true
                                                           :value (utils/format-number (Math/abs required))}])))]
     (let [remaining-budget           (- available-budget (:investment change))
           building-costs-for-action? (case (:action change)
                                        "increase-provider" (not (empty? (:increasing-costs props)))
                                        "upgrade-provider" (and (pos? (:upgrade-budget props)) (not (empty? (:increasing-costs props))))
                                        (some? (:building-costs props)))
           suggested-cost             (suggest-investment change props)]
       [:div
        [common2/numeric-text-field {:type "number"
                                     :label "Investment"
                                     :on-change #(dispatch [:scenarios/save-key [:changeset-dialog :change :investment] %])
                                     :not-valid? (< available-budget (:investment change))
                                     :value (or (:investment change) "")}]
        [common2/numeric-text-field {:label "Available budget"
                                     :type "number"
                                     :read-only true
                                     :value (if (pos? remaining-budget) remaining-budget 0)}]
        (when (or building-costs-for-action? (:action-cost provider))
          [:p.text-helper {:on-click #(dispatch [:scenarios/save-key [:changeset-dialog :change :investment] suggested-cost])}
           "Suggested investment according to project configuration: " suggested-cost])])]))


(defn- action->title
  [name]
  (str/join " " (map str/capitalize (str/split name #"-"))))

(defn changeset-dialog
  [project scenario]
  (let [provider   (subscribe [:scenarios/changeset-dialog])
        view-state (subscribe [:scenarios/view-state])]
    (fn [{:keys [config] :as project} scenario]
      (let [open? (= @view-state :changeset-dialog)
            action (get-in @provider [:change :action])
            budget (get-in config [:actions :budget])]
        (dialog {:open?       open?
                 :acceptable? (and ((fnil pos? 0) (get-in @provider [:change :investment]))
                                   ((fnil pos? 0) (get-in @provider [:change :capacity])))
                 :title       (action->title action)
                 :content     (when open?
                                (changeset-dialog-content
                                 (assoc @provider
                                        :available-budget (- budget (:investment scenario)))
                                 {:project-capacity (get-in config [:providers :capacity])
                                  :upgrade-budget   (get-in config [:actions :upgrade-budget])
                                  :building-costs   (sort-by :capacity (get-in config [:actions :build]))
                                  :increasing-costs (sort-by :capacity (get-in config [:actions :upgrade]))}))
                 :delete-fn   #(dispatch [:scenarios/delete-change (:id @provider)])
                 :accept-fn   #(dispatch [:scenarios/accept-changeset-dialog])
                 :cancel-fn   #(dispatch [:scenarios/cancel-dialog])})))))

(defn new-provider-button
  [state computing?]
  [:div (if computing?
          {:class-name "border-btn-floating border-btn-floating-animated"}
          {:class-name "border-btn-floating"})
   [m/Fab {:class-name "btn-floating"
           :on-click #(dispatch [:scenarios.new-action/toggle-options])}
    (cond computing? "stop"
          (= state :new-provider) "cancel"
          :default "domain")]])

(defn create-new-action-component
  [state computing?]
  (let [open (subscribe [:scenarios.new-action/options])]
    (fn [state computing?]
      [:div.scenario-settings
       [new-provider-button state computing?]
       [m/Menu (when @open {:class "options-menu mdc-menu--open"})
        [m/MenuItem
         {:on-click #(dispatch [:scenarios.new-action/simple-create-provider])}
         "Create one"]
        [m/MenuItem
         {:on-click #(dispatch [:scenarios.new-provider/fetch-suggested-locations])}
         "Get suggestions for new provider"]
        [m/MenuItem
         {:on-click #(dispatch [:scenarios.new-action/fetch-suggested-providers-to-improve])}
         "Get suggestions for improve existing provider"]]])))

(defn scenario-settings
  [state]
  (let [open (subscribe [:scenarios/scenario-menu-settings])]
    (fn [state]
      [:div.scenario-settings
       [m/Button
        {:on-click #(dispatch [:scenarios/show-scenario-settings])}
        [m/Icon "settings"]]
       [m/MenuAnchor
        [:div]
        [m/Menu (when @open {:class "mdc-menu--open"})
         [m/MenuItem
          {:on-click  #(dispatch [:scenarios/open-rename-dialog])}
          "Rename scenario"]
         [m/MenuItem
          {:on-click #(dispatch [:scenarios/open-delete-dialog])}
          "Delete scenario"]]]])))

(defn delete-scenario-dialog
  [state current-scenario]
  [dialog {:open? (= state :delete-scenario)
           :title (str "Delete " (:name current-scenario))
           :cancel-fn #(dispatch [:scenarios/cancel-dialog])
           :delete-fn #(dispatch [:scenarios/delete-current-scenario])
           :content [:p "Do you want to remove current scenario from project?"]}])
