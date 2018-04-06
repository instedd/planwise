(ns planwise.client.projects2.components.settings
  (:require [reagent.core :as r]
            [re-frame.core :refer [subscribe dispatch] :as rf]
            [re-com.core :as rc]
            [planwise.client.asdf :as asdf]
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
  (let [props (merge {:choices @(rf/subscribe [:regions/list])
                      :label-fn :name
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
    [m/TextField {:type "text"
                  :label label
                  :on-change change-fn
                  :value value}]))

(defn- project-start-button
  [_ project]
  [m/Button {:id "start-project"
             :type "button"
             :on-click (utils/prevent-default #(dispatch [:projects2/start-project (:id project)]))}
   (if (= (keyword (:state project)) :started) "Started ..." "Start")])

(defn edit-current-project
  []
  (let [current-project (subscribe [:projects2/current-project])]
    (fn []
      [ui/fixed-width (common2/nav-params)
       [ui/panel {}
        [m/Grid {}
         [m/GridCell {:span 6}
          [:form.vertical
           [:h2 "Goal"]
           [current-project-input "Goal" [:name] identity]
           [regions-dropdown-component {:label "Region"
                                        :on-change #(dispatch [:projects2/save-key :region-id %])
                                        :model (:region-id @current-project)}]
           [:h2 "Demand"]
           [population-dropdown-component {:label "Sources"
                                           :value (:population-source-id @current-project)
                                           :on-change #(dispatch [:projects2/save-key :population-source-id %])}]
           [current-project-input "Target" [:config :demographics :target] valid-input]
           [current-project-input "Unit" [:config :demographics :unit-name] identity]
           [:h2 "Sites"]
           [datasets-dropdown-component {:label "Dataset"
                                         :value (:dataset-id @current-project)
                                         :on-change #(dispatch [:projects2/save-key :dataset-id %])}]
           [:h2 "Coverage"]
           [coverage-algorithm-filter-options {:coverage-algorithm (:coverage-algorithm @current-project)
                                               :value (get-in @current-project [:config :coverage :filter-options])
                                               :on-change #(dispatch [:projects2/save-key [:config :coverage :filter-options] %])
                                               :empty [:div "First choose dataset."]}]
           [:h2 "Actions"]
           [current-project-input "Budget" [:config :actions :budget] valid-input]]
          [project-start-button {} @current-project]]]]])))
