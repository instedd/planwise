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
            [planwise.client.datasets2.components.dropdown :refer [datasets-dropdown-component]]
            [planwise.client.mapping :refer [static-image fullmap-region-geo]]
            [planwise.client.population :refer [population-dropdown-component]]
            [planwise.client.routes :as routes]
            [planwise.client.ui.common :as ui]
            [planwise.client.ui.filter-select :as filter-select]
            [planwise.client.ui.rmwc :as m]
            [planwise.client.utils :as utils]))

;;------------------------------------------------------------------------
;;Current Project updating

(defn- valid-input
  [inp]
  (let [value (js/parseInt inp)]
    (if (and (number? value) (not (js/isNaN value))) value nil)))

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
  ([label path transform]
   (current-project-input label path transform {:disabled false}))
  ([label path transform {:keys [disabled]}]
   (let [current-project (rf/subscribe [:projects2/current-project])
         value           (or (get-in @current-project path) "")
         change-fn       #(rf/dispatch-sync [:projects2/save-key path (-> % .-target .-value transform)])]
     [m/TextField {:type      "text"
                   :label     label
                   :on-change change-fn
                   :value     value
                   :disabled  disabled}])))

(defn- project-start-button
  [_ project]
  [m/Button {:id         "start-project"
             :type       "button"
             :unelevated "unelevated"
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
   (cond
     (false? read-only) [m/ChipIcon {:use "close"
                                     :on-click #(dispatch [:projects2/delete-tag index])}])])

(defn- tag-set
  [tags read-only]
  [m/ChipSet {:class "tags"}
   (for [[index tag] (map-indexed vector tags)]
     [tag-chip {:key index} index tag read-only])])

(defn tag-input []
  (let [value (r/atom "")]
    (fn []
      [m/TextField {:type "text"
                    :placeholder "Type tag for filtering sites"
                    :on-key-press (fn [e] (when (and (= (.-charCode e) 13) (not (blank? @value)))
                                            (dispatch [:projects2/save-tag @value])
                                            (reset! value "")))
                    :on-change #(reset! value (-> % .-target .-value))
                    :value @value}])))

(defn- section-header
  [number title]
  [:div {:class-name "step-header"}
   [:h2 [:span title]]])

(defn current-project-settings-view
  [{:keys [read-only]}]
  (let [current-project (subscribe [:projects2/current-project])
        tags            (subscribe [:projects2/tags])]
    (fn [{:keys [read-only]}]
      [m/Grid {}
       [m/GridCell {:span 6}
        [:form.vertical
         [:section {:class-name "project-settings-section"}
          [section-header 1 "Goal"]
          [current-project-input "Goal" [:name] identity]
          [m/TextFieldHelperText {:persistent true} "Enter the goal for this project"]

          [regions-dropdown-component {:label     "Region"
                                       :on-change #(dispatch [:projects2/save-key :region-id %])
                                       :model     (:region-id @current-project)
                                       :disabled? read-only}]]

         [:section {:class-name "project-settings-section"}
          [section-header 2 "Demand"]
          [population-dropdown-component {:label     "Sources"
                                          :value     (:population-source-id @current-project)
                                          :on-change #(dispatch [:projects2/save-key :population-source-id %])
                                          :disabled?  read-only}]

          [current-project-input "Unit" [:config :demographics :unit-name] identity {:disabled read-only}]
          [current-project-input "Target" [:config :demographics :target] valid-input {:disabled read-only}]
          [m/TextFieldHelperText {:persistent true} (str "Percentage of population that should be considered " (get-in @current-project [:config :demographics :unit-name]))]]

         [:section {:class-name "project-settings-section"}
          [section-header 3 "Sites"]
          [datasets-dropdown-component {:label     "Dataset"
                                        :value     (:dataset-id @current-project)
                                        :on-change #(dispatch [:projects2/save-key :dataset-id %])
                                        :disabled? read-only}]

          [current-project-input "Capacity workload" [:config :sites :capacity] valid-input {:disabled read-only}]
          [m/TextFieldHelperText {:persistent true} (str "How many " (get-in @current-project [:config :demographics :unit-name]) " can be handled per site capacity")]

          (cond
            (false? read-only) [tag-input])
          [:label "Tags: " [tag-set @tags read-only]]]

         [:section {:class-name "project-settings-section"}
          [section-header 4 "Coverage"]
          [coverage-algorithm-filter-options {:coverage-algorithm (:coverage-algorithm @current-project)
                                              :value              (get-in @current-project [:config :coverage :filter-options])
                                              :on-change          #(dispatch [:projects2/save-key [:config :coverage :filter-options] %])
                                              :empty              [:div {:class-name " no-dataset-selected"} "First choose dataset."]
                                              :disabled?          read-only}]]

         [:section {:class-name "project-settings-section"}
          [section-header 5 "Actions"]
          [current-project-input "Budget" [:config :actions :budget] valid-input {:disabled read-only}]]]]])))

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
