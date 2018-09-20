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
        (let [current-capacity        (if increase? (- capacity initial-capacity) capacity)
              total-provider-capacity (if idle? (- current-capacity free-capacity) (+ capacity required-capacity))
              extra-capacity          (:capacity change)
              required                (Math/ceil (- total-provider-capacity extra-capacity))]
          (cond (not (neg? required)) [:div.inline
                                       [common2/text-field {:label "Required capacity"
                                                            :read-only true
                                                            :value (utils/format-number (Math/abs required))}]
                                       [:p.text-helper "Unsatisfied demand: " (utils/format-number (* (:project-capacity props) (Math/abs required)))]]
                (neg? required)       [common2/text-field {:label "Free capacity"
                                                           :read-only true
                                                           :value (utils/format-number (Math/abs required))}])))]
     [:div
      [common2/numeric-text-field {:type "number"
                                   :label "Investment"
                                   :on-change #(dispatch [:scenarios/save-key [:changeset-dialog :change :investment] %])
                                   :not-valid? (< available-budget (:investment change))
                                   :value (or (:investment change) "")}]]]))


(defn- action->title
  [name]
  (str/join " " (map str/capitalize (str/split name #"-"))))

(defn changeset-dialog
  [project scenario]
  (let [provider   (subscribe [:scenarios/changeset-dialog])
        view-state (subscribe [:scenarios/view-state])
        budget     (get-in project [:config :actions :budget])]
    (fn [scenario budget]
      (let [open? (= @view-state :changeset-dialog)
            action (get-in @provider [:change :action])]
        (dialog {:open?       open?
                 :acceptable? (and ((fnil pos? 0) (get-in @provider [:change :investment]))
                                   ((fnil pos? 0) (get-in @provider [:change :capacity])))
                 :title       (action->title action)
                 :content     (when open?
                                (changeset-dialog-content
                                 (assoc @provider
                                        :available-budget (- budget (:investment scenario)))
                                 {:project-capacity (get-in project [:config :providers :capacity])}))
                 :delete-fn   #(dispatch [:scenarios/delete-change (:id @provider)])
                 :accept-fn   #(dispatch [:scenarios/accept-changeset-dialog])
                 :cancel-fn   #(dispatch [:scenarios/cancel-dialog])})))))

(defn new-provider-button
  [state computing?]
  [:div (if computing?
          {:class-name "border-btn-floating border-btn-floating-animated"}
          {:class-name "border-btn-floating"})
   [m/Fab {:class-name "btn-floating"
           :on-click #(dispatch [:scenarios.new-provider/toggle-options])}
    (cond computing? "stop"
          (= state :new-provider) "cancel"
          :default "domain")]])

(defn create-new-provider-component
  [state computing?]
  (let [open (subscribe [:scenarios.new-provider/options])]
    (fn [state computing?]
      [m/MenuAnchor
       [new-provider-button state computing?]
       [m/Menu (when @open {:class "options-menu mdc-menu--open"})
        [m/MenuItem
         {:on-click #(dispatch [:scenarios.new-provider/simple-creation])}
         "Create one"]
        [m/MenuItem
         {:on-click #(dispatch [:scenarios.new-provider/fetch-suggested-locations])}
         "Get suggestions"]]])))

(defn upgrade-provider-button
  [provider]
  [m/Fab [m/Icon "arrow_upward"]
   {:on-click #(dispatch [:scenarios/provider-action :upgrade provider])}])

(defn increase-provider-button
  [provider]
  [m/Fab [m/Icon "add"]
   {:on-click #(dispatch [:scenarios/provider-action :increase provider])}])
