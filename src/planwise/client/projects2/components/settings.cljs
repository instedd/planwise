(ns planwise.client.projects2.components.settings
  (:require [reagent.core :as r]
            [re-frame.core :refer [subscribe dispatch] :as rf]
            [re-com.core :as rc]
            [clojure.string :refer [blank?]]
            [planwise.client.asdf :as asdf]
            [planwise.client.dialog :refer [dialog]]
            [planwise.client.components.common2 :as common2]
            [planwise.client.projects2.components.common :refer [delete-project-dialog]]
            [planwise.client.coverage :refer [coverage-algorithm-filter-options]]
            [planwise.client.providers-set.components.dropdown :refer [providers-set-dropdown-component]]
            [planwise.client.sources.components.dropdown :refer [sources-dropdown-component]]
            [planwise.client.mapping :refer [static-image fullmap-region-geo]]
            [planwise.client.routes :as routes]
            [planwise.client.ui.common :as ui]
            [planwise.client.ui.filter-select :as filter-select]
            [planwise.client.ui.rmwc :as m]
            [planwise.client.utils :as utils]
            [clojure.spec.alpha :as s]))

;;------------------------------------------------------------------------
;;Current Project updating

(defn- regions-dropdown-component
  [attrs]
  (let [props (merge {:choices   @(rf/subscribe [:regions/list])
                      :label-fn  :name
                      :render-fn (fn [region] [:div
                                               [:span (:name region)]
                                               [:span.option-context (:country-name region)]])}
                     attrs)]
    (into [filter-select/single-dropdown] (mapcat identity props))))

(defn- current-project-input
  ([label path field]
   (current-project-input label path field {:disabled false}))
  ([label path field {:keys [disabled class type]}]
   (let [current-project (rf/subscribe [:projects2/current-project])]
     [common2/text-field {:label label
                          :field field
                          :type type
                          :on-change (fn [e] (rf/dispatch-sync [:projects2/save-key path e]))
                          :class class
                          :value (str (get-in @current-project path))
                          :disabled disabled}])))

(defn- project-start-button
  [_ project]
  [m/Button {:id         "start-project"
             :type       "button"
             :unelevated "unelevated"
             :disabled   (not (s/valid? :planwise.model.project/starting project))
             :on-click   (utils/prevent-default #(dispatch [:projects2/start-project (:id project)]))}
   (if (= (keyword (:state project)) :started) "Started ..." "Start")])

(defn- project-delete-button
  [state]
  [m/Button {:type     "button"
             :theme    ["text-secondary-on-secondary-light"]
             :on-click #(reset! state true)} "Delete"])

(defn- tag-chip
  [props index input read-only]
  [m/Chip props [m/ChipText input]
   (when-not read-only [m/ChipIcon {:use "close"
                                    :on-click #(dispatch [:projects2/delete-tag index])}])])

(defn- tag-set
  [tags read-only]
  [m/ChipSet {:class "tags"}
   (for [[index tag] (map-indexed vector tags)]
     [tag-chip {:key (str "tag-" index)} index tag read-only])])

(defn tag-input []
  (let [value (r/atom "")]
    (fn []
      [common2/text-field {:type :default
                           :placeholder "Type tag for filtering providers"
                           :on-key-press (fn [e] (when (and (= (.-charCode e) 13) (not (blank? @value)))
                                                   (dispatch [:projects2/save-tag @value])
                                                   (reset! value "")))
                           :on-change #(reset! value (-> % .-target .-value))
                           :value @value}])))

(defn- count-providers
  [tags {:keys [provider-set-id providers region-id]}]
  (let [{:keys [total filtered]} providers]
    (cond (nil? region-id) [:p "Select region first."]
          (nil? provider-set-id) [:p "Select provider set first."]
          :else [:p "Selected providers: " filtered " / " total])))

(defn- section-header
  [number title]
  [:div {:class-name "step-header"}
   [:h2 [:span title]]])


;-------------------------------------------------------------------------------------------
; Actions
(defn- show-action
  [_ {:keys [idx action-name capacity investment] :as action}]
  [:div {:class "project-setting"}
   [m/Button {:type "button"
              :theme    ["text-secondary-on-secondary-light"]
              :on-click #(dispatch [:projects2/delete-action action-name idx])}
    [m/Icon "clear"]]
   (when (= action-name :build) "with a capacity of ")
   [current-project-input "" [:config :actions action-name idx :capacity]  :numeric {:class "action-input"}]
   "would cost"
   [current-project-input "" [:config :actions action-name idx :investment] :numeric {:class "action-input"}]])

(defn- listing-actions
  [action-name list]
  [:div
   (for [[index action] (map-indexed vector list)]
     [show-action {:key (str action-name "-" index)} (assoc action :action-name action-name :idx index)])
   [m/Button  {:type "button"
               :theme    ["text-secondary-on-secondary-light"]
               :on-click #(dispatch [:projects2/create-action action-name])} [m/Icon "add"] "Add Option"]])

;-------------------------------------------------------------------------------------------

(defn current-project-settings-view
  [{:keys [read-only]}]
  (let [current-project (subscribe [:projects2/current-project])
        build-actions   (subscribe [:projects2/build-actions])
        upgrade-actions (subscribe [:projects2/upgrade-actions])
        tags            (subscribe [:projects2/tags])]
    (fn [{:keys [read-only]}]
      [m/Grid {}
       [m/GridCell {:span 6}
        [:form.vertical
         [:section {:class-name "project-settings-section"}
          [section-header 1 "Goal"]
          [current-project-input "Goal" [:name] :default]
          [m/TextFieldHelperText {:persistent true} "Enter the goal for this project"]

          [regions-dropdown-component {:label     "Region"
                                       :on-change #(dispatch [:projects2/save-key :region-id %])
                                       :model     (:region-id @current-project)
                                       :disabled? read-only}]]

         [:section {:class-name "project-settings-section"}
          [section-header 2 "Demand"]
          [sources-dropdown-component {:label     "Sources"
                                       :value     (:source-set-id @current-project)
                                       :on-change #(dispatch [:projects2/save-key :source-set-id %])
                                       :disabled?  read-only}]

          [current-project-input "Unit" [:config :demographics :unit-name] :default {:disabled read-only}]
          [current-project-input "Target" [:config :demographics :target] :numeric {:disabled read-only
                                                                                    :type :percentage}]
          [m/TextFieldHelperText {:persistent true} (str "Percentage of population that should be considered " (get-in @current-project [:config :demographics :unit-name]))]]

         [:section {:class-name "project-settings-section"}
          [section-header 3 "Providers"]
          [providers-set-dropdown-component {:label     "Provider Set"
                                             :value     (:provider-set-id @current-project)
                                             :on-change #(dispatch [:projects2/save-key :provider-set-id %])
                                             :disabled? read-only}]

          [current-project-input "Capacity workload" [:config :providers :capacity] :numeric {:disabled read-only
                                                                                              :type :float}]
          [m/TextFieldHelperText {:persistent true} (str "How many " (get-in @current-project [:config :demographics :unit-name]) " can be handled per provider capacity")]

          (when-not read-only [tag-input])
          [:label "Tags: " [tag-set @tags read-only]]
          [count-providers @tags @current-project]]

         [:section {:class-name "project-settings-section"}
          [section-header 4 "Coverage"]
          [coverage-algorithm-filter-options {:coverage-algorithm (:coverage-algorithm @current-project)
                                              :value              (get-in @current-project [:config :coverage :filter-options])
                                              :on-change          #(dispatch [:projects2/save-key [:config :coverage :filter-options] %])
                                              :empty              [:div {:class-name " no-provider-set-selected"} "First choose provider-set."]
                                              :disabled?          read-only}]]

         [:section {:class-name "project-settings-section"}
          [:div [:p [m/Icon "account_balance"] "Available budget"]]
          [current-project-input "" [:config :actions :budget] :numeric {:disabled read-only :class "project-setting"}]
          [m/TextFieldHelperText {:persistent true} "Planwise will keep explored scenarios below this maximum budget"]

          [:div [:p [m/Icon "domain"] "Building a new provider..."]]
          [listing-actions :build @build-actions]

          [:div [:p [m/Icon "arrow_upward"] "Upgrading a provider so that it can satisfy demand would cost..."]]
          [current-project-input "" [:config :actions :upgrade-budget] :numeric {:disabled read-only :class "project-setting"}]

          [:div [:p [m/Icon "add"] "Increase the capactiy of a provider by..."]]
          [listing-actions :upgrade @upgrade-actions]]]]])))


(defn edit-current-project
  []
  (let [current-project (subscribe [:projects2/current-project])
        delete?         (r/atom false)
        hide-dialog     (fn [] (reset! delete? false))]
    (fn []
      [ui/fixed-width (common2/nav-params)
       [ui/panel {}

        [current-project-settings-view {:read-only false}]

        [:div {:class-name "project-settings-actions"}
         [project-delete-button delete?]
         [project-start-button {} @current-project]]]
       [delete-project-dialog {:open? @delete?
                               :cancel-fn hide-dialog
                               :delete-fn #(dispatch [:projects2/delete-project (:id @current-project)])}]])))
