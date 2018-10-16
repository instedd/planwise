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
  ([label path type]
   (current-project-input label path type {:disabled false}))
  ([label path type other-props]
   (let [current-project (rf/subscribe [:projects2/current-project])
         value           (or (get-in @current-project path) "")
         change-fn       #(rf/dispatch-sync [:projects2/save-key path %])
         props (merge other-props
                      {:label     label
                       :on-change (comp change-fn (fn [e] (-> e .-target .-value)))
                       :value     value})]
     (case type
       "number" [common2/numeric-field (assoc props :on-change change-fn)]
       [common2/text-field props]))))

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
  [m/ChipSet {:class-name "tag-set"}
   (for [[index tag] (map-indexed vector tags)]
     [tag-chip {:key (str "tag-" index)} index tag read-only])])

(defn tag-input [props]
  (let [value (r/atom "")]
    (fn [props]
      [common2/text-field (merge props
                                 {:placeholder "Type tag for filtering providers"
                                  :component-class "tag-input"
                                  :on-key-press (fn [e] (when (and (= (.-charCode e) 13) (not (blank? @value)))
                                                          (dispatch [:projects2/save-tag @value])
                                                          (reset! value "")))
                                  :on-change #(reset! value (-> % .-target .-value))
                                  :value @value})])))

(defn- count-providers
  [tags {:keys [provider-set-id providers region-id]}]
  (let [{:keys [total filtered]} providers]
    (cond (nil? region-id) [:p "Select region first."]
          (nil? provider-set-id) [:p "Select provider set first."]
          :else [m/TextFieldHelperText {:persistent true :class-name "grey-text"} "Selected providers: " filtered " / " total])))

(defn- create-and-show-tags
  [project tags read-only]
  [:div
   [:div.tags
    (when-not read-only [tag-input {:extra-left-content [tag-set tags read-only]}])]
   [count-providers tags project]])

(defn- section-header
  [number title]
  [:div {:class-name "step-header"}
   [:h2 [:span title]]])

;-------------------------------------------------------------------------------------------
; Actions
(defn- show-action
  [_ {:keys [idx action-name capacity investment capacity-unit] :as action} props]
  [:div.fixed-input-and-text
   [:p (merge
        {:on-click #(dispatch [:projects2/delete-action action-name idx])}
        props)
    [m/Icon {:class-name "display-icon add-padding"} "clear"]]
   (when (= action-name :build) [:div "with a capacity of "])
   [current-project-input "" [:config :actions action-name idx :capacity] "number"
    (assoc props :extra-right-content [:i.mdc-text-field__input.fixed-icon (str "  " capacity-unit)])]
   [:div.text-passage "would cost"]
   [current-project-input "" [:config :actions action-name idx :investment] "number"
    (assoc props :extra-left-content [:i.mdc-text-field__input.fixed-icon "K  "])]])

(defn- listing-actions
  [project {:keys [read-only? action-name list]}]
  [:div
   (for [[index action] (map-indexed vector list)]
     [show-action {:key (str action-name "-" index)}
      (assoc action :action-name action-name
             :idx index
             :capacity-unit (get-in project [:config :providers :capacity-unit]))
      {:disabled read-only?}])
   [:div.add-action-button
    [m/Button  {:type "button"
                :disabled read-only?
                :theme    ["text-secondary-on-secondary-light"]
                :on-click #(dispatch [:projects2/create-action action-name])}
     [m/Icon {:class-name "display-icon"} "add"]
     "Add Option"]]])

;-------------------------------------------------------------------------------------------
(defn config-goal
  [current-project read-only]
  [:section {:class-name "project-settings-section"}
   [section-header 1 "Goal"]
   [current-project-input "Goal" [:name] "text"]
   [m/TextFieldHelperText {:persistent true} "Enter the goal for this project"]

   [regions-dropdown-component {:label     "Location"
                                :on-change #(dispatch [:projects2/save-key :region-id %])
                                :model     (:region-id current-project)
                                :disabled? read-only}]])

(defn config-consumers
  [current-project read-only]
  [:section {:class-name "project-settings-section"}
   [section-header 2 "Consumers"]
   [:div.section-body
    [sources-dropdown-component {:label     "Source"
                                 :value     (:source-set-id current-project)
                                 :on-change #(dispatch [:projects2/save-key :source-set-id %])
                                 :disabled?  read-only}]
    [current-project-input "Consumers unit" [:config :demographics :source-unit-name] "text" {:disabled read-only}]
    [m/TextFieldHelperText {:persistent true} "How do you refer to the filtered population? (eg. \" women of childbearing age \")"]
    [:div.fixed-input-and-text
     [:div.small-sized-input
      (let [target-props {:disabled read-only
                          :disable-floating-label true
                          :sub-type :percentage
                          :extra-right-content [:i.mdc-text-field__input.fixed-icon " %  "]}]
        [current-project-input "Target" [:config :demographics :target] "number" target-props])]
     [:div.fixed-text (str " of population that should be considered "
                           (get-in current-project [:config :demographics :source-unit-name]))]]

    [current-project-input "Demand unit" [:config :demographics :demand-unit-name] "text" {:disabled read-only}]
    [m/TextFieldHelperText {:persistent true}  "How do you refer to the unit in your demand? (eg. \" visits per month \")"]]])

(defn config-providers
  [current-project read-only tags? tags]
  (let [provider-unit (get-in current-project [:config :providers :provider-unit])
        demand-unit   (get-in current-project [:config :demographics :demand-unit-name])
        capacity-unit (get-in current-project [:config :providers :capacity-unit])]
    [:section {:class-name "project-settings-section"}
     [section-header 3 "Providers"]
     [:div.section-body
      [providers-set-dropdown-component {:label     "Dataset"
                                         :value     (:provider-set-id current-project)
                                         :on-change #(dispatch [:projects2/save-key :provider-set-id %])
                                         :disabled? read-only}]

      [current-project-input "Providers unit" [:config :providers :provider-unit] "text" {:disabled read-only}]
      [m/TextFieldHelperText {:persistent true} "How do you refer to your providers? (eg. \"hospitals \")"]

      [current-project-input "Capacity unit" [:config :providers :capacity-unit] "text" {:disabled read-only}]
      (when provider-unit
        [m/TextFieldHelperText {:persistent true} "What's the unit of capacity for " provider-unit " ? (eg. \"beds \")"])

      [current-project-input "Capacity workload" [:config :providers :capacity] "number" {:disabled read-only :sub-type :float}]
      [m/TextFieldHelperText {:persistent true} (str "How many " (get-in current-project [:config :demographics :unit-name]) " can be handled per provider capacity")]
      [:div
       [:div.floating-label "Capacity factor"]
       [:div.fixed-input-and-text
;;Singularize capacity unit
        [:div.fixed-text
         (str "Each " capacity-unit " in a " provider-unit " will provide service for   ")]
        [:div.small-sized-input
         (let [props {:disabled read-only
                      :disable-floating-label true
                      :sub-type :float
                      :class-name "centered-text"}]
           [current-project-input "" [:config :demographics :target] "number" props])]
        [:div.fixed-text (str demand-unit  " per year ")]]]
      [m/Radio {:checked (false? @tags?)
                :disabled read-only
                :value "no-tags"
                :on-click #(reset! tags? false)}
       "All provider units in the dataset can provide the service"]
      [m/Radio {:checked @tags?
                :disabled read-only
                :value "tags"
                :on-click #(reset! tags? true)}
       "Only some provider units can provide the service"]
      (when @tags? [create-and-show-tags current-project tags read-only])]]))

(defn config-coverage
  [current-project read-only]
  [:section {:class-name "project-settings-section"}
   [section-header 4 "Coverage"]
   [:div.section-body
    [:div.text-introduction
     "These values will be used to estimate the geographic coverage that your current sites are providing.
      That in turn will allow Planwise to calculate areas out of reach."]
    [coverage-algorithm-filter-options {:coverage-algorithm (:coverage-algorithm current-project)
                                        :value              (get-in current-project [:config :coverage :filter-options])
                                        :on-change          #(dispatch [:projects2/save-key [:config :coverage :filter-options] %])
                                        :empty              [:div {:class-name " no-provider-set-selected"} "First choose provider-set."]
                                        :disabled?          read-only}]]])

(defn action-container
  [{:keys [title icon content]}]
  [:div.container
   [:div.action-title [m/Icon {:class-name "action-icon"} icon] title]
   [:div.action-content
    content]])

(defn config-building-options
  [project read-only build-actions upgrade-actions]
  [:section {:class-name "project-settings-section"}
   [section-header 5 "Actions"]
   [:div.section-body
    [:div.text-introduction
     "Potential actions to increase access to services.
     Planwise will use these to explore and recommend the best alternatives."]
    [:div.actions-form
     [action-container
      {:icon "account_balance"
       :title "Available budget"
       :content [:div
                 [:div.fixed-input-and-text
                  [current-project-input "" [:config :actions :budget] "number" {:disabled read-only
                                                                                 :sub-type :float
                                                                                 :extra-left-content [:i.mdc-text-field__input.fixed-icon "K  "]}]]
                 [m/TextFieldHelperText {:persistent true :class "grey-text"}
                  "Planwise will keep explored scenarios below this maximum budget"]]}]
     [action-container
      {:icon "domain"
       :title "Building a new provider..."
       :content [listing-actions project {:read-only?  read-only
                                          :action-name :build
                                          :list        build-actions}]}]
     [action-container
      {:icon "arrow_upward"
       :title "Upgrading a provider so that it can satisfy demand would cost..."
       :content [:div
                 [:div.fixed-input-and-text
                  [current-project-input "" [:config :actions :upgrade-budget] "number" {:disabled read-only
                                                                                         :sub-type :float
                                                                                         :extra-left-content [:i.mdc-text-field__input.fixed-icon "K  "]}]]]}]
     [action-container
      {:icon "add"
       :title "Increase the capactiy of a provider by..."
       :content [listing-actions project {:read-only?   read-only
                                          :action-name :upgrade
                                          :list        upgrade-actions}]}]]]])

(defn current-project-settings-view
  [{:keys [read-only]}]
  (let [current-project (subscribe [:projects2/current-project])
        build-actions   (subscribe [:projects2/build-actions])
        upgrade-actions (subscribe [:projects2/upgrade-actions])
        show-tags?      (r/atom nil)
        tags            (subscribe [:projects2/tags])]
    (fn [{:keys [read-only]}]
      [m/Grid {}
       [m/GridCell {:span 6}
        [:form.vertical
         [config-goal @current-project read-only]
         [config-consumers @current-project read-only]
         [config-providers @current-project read-only show-tags? @tags]
         [config-coverage @current-project read-only]
         [config-building-options @current-project read-only @build-actions @upgrade-actions]]]])))


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
