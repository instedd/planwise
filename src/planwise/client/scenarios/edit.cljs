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
  [{:keys [available-budget change]}]
  [:div
   [:h2 "Investment"]
   [common2/numeric-text-field {:type "number"
                                :on-change #(dispatch [:scenarios/save-key [:changeset-dialog :change :investment] %])
                                :not-valid? (< available-budget (:investment change))
                                :value (or (:investment change) "")}]

   [:h2 "Capacity"]
   [common2/numeric-text-field {:type "number"
                                :on-change  #(dispatch [:scenarios/save-key  [:changeset-dialog :change :capacity] %])
                                :value (or (:capacity change) "")}]])
(defn changeset-dialog
  [scenario budget]
  (let [provider       (subscribe [:scenarios/changeset-dialog])
        view-state     (subscribe [:scenarios/view-state])]
    (fn [scenario budget]
      (when (= @view-state :changeset-dialog)
        (dialog {:open? true
                 :acceptable? (and ((fnil pos? 0) (get-in @provider [:change :investment])) ((fnil pos? 0) (get-in @provider [:change :capacity])))
                 :title "Edit Provider"
                 :content (changeset-dialog-content (assoc @provider :available-budget (- budget (:investment scenario))))
                 :delete-fn #(dispatch [:scenarios/delete-change (:id @provider)])
                 :accept-fn #(dispatch [:scenarios/accept-changeset-dialog])
                 :cancel-fn #(dispatch [:scenarios/cancel-dialog])})))))

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
