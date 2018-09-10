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
        view-state (subscribe [:scenarios/view-state])]
    (fn []
      (dialog {:open? (= @view-state :rename-dialog)
               :title "Rename scenario"
               :content  [common2/text-field {:label "Name"
                                              :value (str (:value @rename-dialog))
                                              :on-change #(dispatch [:scenarios/save-key
                                                                     [:rename-dialog :value] (-> % .-target .-value)])}]
               :accept-fn  #(dispatch [:scenarios/accept-rename-dialog])
               :cancel-fn  #(dispatch [:scenarios/cancel-dialog])}))))

(defn changeset-dialog-content
  [{:keys [investment available-budget capacity]}]
  [:div
   [:h2 "Investment"]
   [common2/numeric-text-field {:type "number"
                                :on-change #(dispatch [:scenarios/save-key [:changeset-dialog :investment] %])
                                :focus-extra-class (when (< available-budget investment) " invalid-input")
                                :value (or investment "")}]

   [:h2 "Capacity"]
   [common2/numeric-text-field {:type "number"
                                :on-change  #(dispatch [:scenarios/save-key  [:changeset-dialog :capacity] %])
                                :value (or capacity "")}]])
(defn changeset-dialog
  [scenario budget]
  (let [provider       (subscribe [:scenarios/changeset-dialog])
        view-state     (subscribe [:scenarios/view-state])
        provider-index (subscribe [:scenarios/changeset-index])]
    (fn [scenario budget]
      (dialog {:open? (= @view-state :changeset-dialog)
               :disable-accept-button (or (nil? (:capacity @provider)) (nil? (:investment @provider)))
               :title "Edit Provider"
               :content (changeset-dialog-content (assoc @provider :available-budget (- budget (:investment scenario))))
               :delete-fn #(dispatch [:scenarios/delete-provider @provider-index])
               :accept-fn #(dispatch [:scenarios/accept-changeset-dialog])
               :cancel-fn #(dispatch [:scenarios/cancel-dialog])}))))

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
