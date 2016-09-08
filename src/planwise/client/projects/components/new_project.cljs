(ns planwise.client.projects.components.new-project
  (:require [re-frame.core :refer [subscribe dispatch]]
            [reagent.core :as r]
            [clojure.string :as str]
            [leaflet.core :refer [map-widget]]
            [planwise.client.mapping :refer [default-base-tile-layer
                                             static-image
                                             map-preview-position]]
            [planwise.client.asdf :as asdf]
            [planwise.client.components.common :as common]
            [planwise.client.components.dropdown :as dropdown]
            [planwise.client.utils :as utils]
            [planwise.client.styles :as styles]))

(defn new-project-dialog []
  (let [view-state (subscribe [:projects/view-state])
        regions (subscribe [:regions/list])
        datasets (subscribe [:datasets/list])
        new-project-goal (r/atom "")
        new-project-region-id (r/atom nil)
        new-project-dataset-id (r/atom nil)
        _ (dispatch [:datasets/load-datasets])
        map-preview-zoom (r/atom 3)
        map-preview-position (r/atom map-preview-position)]
    (fn []
      (let [selected-region-geojson (subscribe [:regions/geojson @new-project-region-id])
            cancel-fn #(dispatch [:projects/cancel-new-project])
            key-handler-fn #(case (.-which %)
                              27 (cancel-fn)
                              nil)]
        [:form.dialog.new-project
         {:on-key-down key-handler-fn
          :on-submit (utils/prevent-default
                      #(dispatch [:projects/create-project
                                  {:goal @new-project-goal
                                   :dataset-id @new-project-dataset-id
                                   :region-id @new-project-region-id}]))}
         [:div.title
          [:h1 "New Project"]
          [common/close-button {:on-click cancel-fn}]]
         [:div.form-control
          [:label "Goal"]
          [:input {:type "text"
                   :required true
                   :autoFocus true
                   :value @new-project-goal
                   :placeholder "Describe your project's goal"
                   :on-key-down key-handler-fn
                   :on-change #(reset! new-project-goal
                                       (-> % .-target .-value str/triml))}]]
         [:div.form-control
          [:label "Dataset"]
          [dropdown/single-dropdown
           :choices (or (asdf/value @datasets) [])
           :label-fn :name
           :filter-box? true
           :on-change #(reset! new-project-dataset-id %)
           :model new-project-dataset-id]]
         [:div.form-control
          [:label "Location"]
          [dropdown/single-dropdown
           :choices @regions
           :label-fn :name
           :render-fn (fn [region] [:div [:span (:name region)] [:span.option-context (:country-name region)]])
           :filter-box? true
           :on-change #(when %
                         (dispatch [:regions/load-regions-with-geo [%]])
                         (reset! new-project-region-id %))
            :model new-project-region-id]
          [map-widget { :position @map-preview-position
                        :zoom @map-preview-zoom
                        :on-position-changed #(reset! map-preview-position %)
                        :on-zoom-changed #(reset! map-preview-zoom %)
                        :width 500
                        :height 300
                        :controls []}
           default-base-tile-layer
           (if @selected-region-geojson
             [:geojson-layer {:data @selected-region-geojson
                              :fit-bounds true
                              :color styles/orange
                              :opacity 0.7
                              :fillOpacity 0.3
                              :weight 4}])]]
         [:div.actions
          [:button.primary
           {:type "submit"
            :disabled (or (= @view-state :creating)
                          (str/blank? @new-project-goal)
                          (str/blank? @new-project-dataset-id)
                          (str/blank? @new-project-region-id))}
           (if (= @view-state :creating)
             "Creating..."
             "Create")]
          [:button.cancel
           {:type "button"
            :on-click cancel-fn}
           "Cancel"]]]))))
