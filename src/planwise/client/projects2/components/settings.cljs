(ns planwise.client.projects2.components.settings
  (:require [reagent.core :as r]
            [re-frame.core :refer [subscribe dispatch] :as rf]
            [re-com.core :as rc]
            [planwise.client.asdf :as asdf]
            [planwise.client.dialog :refer [new-dialog]]
            [planwise.client.components.common2 :as common2]
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
  [label path transform]
  (let [current-project (rf/subscribe [:projects2/current-project])
        value           (or (get-in @current-project path) "")
        change-fn       #(rf/dispatch-sync [:projects2/save-key path (-> % .-target .-value transform)])]
    [m/TextField {:type      "text"
                  :label     label
                  :on-change change-fn
                  :value     value}]))

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

(defn- section-header
  [number title]
  [:div {:class-name "step-header"}
   [:h2 [:span title]]])

(defn edit-current-project
  []
  (let [current-project (subscribe [:projects2/current-project])
        delete?         (r/atom false)]
    (fn []
      [ui/fixed-width (common2/nav-params)
       [ui/panel {}
        [m/Grid {}
         [m/GridCell {:span 6}
          [:form.vertical
           [:section {:class-name "project-settings-section"}
            [section-header 1 "Goal"]
            [current-project-input "Goal" [:name] identity]
            [m/TextFieldHelperText {:persistent true} "Enter the goal for this project"]
            [regions-dropdown-component {:label     "Region"
                                         :on-change #(dispatch [:projects2/save-key :region-id %])
                                         :model     (:region-id @current-project)}]]

           [:h2 "Sites"]
           [:section {:class-name "project-settings-section"}
            [section-header 2 "Demand"]
            [population-dropdown-component {:label     "Sources"
                                            :value     (:population-source-id @current-project)
                                            :on-change #(dispatch [:projects2/save-key :population-source-id %])}]

            [current-project-input "Unit" [:config :demographics :unit-name] identity]
            [current-project-input "Target" [:config :demographics :target] valid-input]
            [m/TextFieldHelperText {:persistent true} (str "Percentage of population that should be considered " (get-in @current-project [:config :demographics :unit-name]))]]

           [:section {:class-name "project-settings-section"}
            [section-header 3 "Sites"]
            [datasets-dropdown-component {:label     "Dataset"
                                          :value     (:dataset-id @current-project)
                                          :on-change #(dispatch [:projects2/save-key :dataset-id %])}]
            [current-project-input "Capacity workload" [:config :sites :capacity] valid-input]
            [m/TextFieldHelperText {:persistent true} (str "How many " (get-in @current-project [:config :demographics :unit-name]) " can be handled per site capacity")]]

           [:section {:class-name "project-settings-section"}
            [section-header 4 "Coverage"]
            [coverage-algorithm-filter-options {:coverage-algorithm (:coverage-algorithm @current-project)
                                                :value              (get-in @current-project [:config :coverage :filter-options])
                                                :on-change          #(dispatch [:projects2/save-key [:config :coverage :filter-options] %])
                                                :empty              [:div {:class-name " no-dataset-selected"} "First choose dataset."]}]]
           [:section {:class-name "project-settings-section"}
            [section-header 5 "Actions"]
            [current-project-input "Budget" [:config :actions :budget] valid-input]]]]]

        [:div {:class-name "project-settings-actions"}
         [project-delete-button delete?]
         [project-start-button {} @current-project]]]

       [new-dialog {:open?     @delete?
                    :title     "Delete Project"
                    :accept-fn #(dispatch [:projects2/delete-project (:id @current-project)])
                    :cancel-fn #(reset! delete? false)
                    :content   [:p "Do you want to delete this project?"]}]])))
