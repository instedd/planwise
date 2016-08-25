(ns planwise.client.projects.components.new-project
  (:require [re-frame.core :refer [subscribe dispatch]]
            [re-com.core :as rc]
            [reagent.core :as r]
            [clojure.string :as str]
            [leaflet.core :refer [map-widget]]
            [planwise.client.mapping :refer [default-base-tile-layer
                                             static-image
                                             bbox-center]]
            [planwise.client.components.common :as common]
            [planwise.client.utils :as utils]
            [planwise.client.styles :as styles]))

(defn new-project-dialog []
  (let [view-state (subscribe [:projects/view-state])
        regions (subscribe [:regions/list])
        datasets (subscribe [:datasets/list])
        new-project-goal (r/atom "")
        new-project-region-id (r/atom (:id (first @regions)))
        new-project-dataset-id (r/atom (:id (first @datasets)))
        _ (dispatch [:regions/load-regions-with-geo [@new-project-region-id]])
        _ (dispatch [:datasets/load-datasets])
        map-preview-zoom (r/atom 5)
        map-preview-position (r/atom (bbox-center (:bbox (first @regions))))]
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
          [rc/single-dropdown
           :choices (or @datasets [])
           :label-fn :name
           :filter-box? true
           :on-change #(reset! new-project-dataset-id %)
           :model new-project-dataset-id]]
         [:div.form-control
          [:label "Location"]
          [rc/single-dropdown
           :choices @regions
           :label-fn :name
           :filter-box? true
           :on-change #(do
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
                          (str/blank? @new-project-goal))}
           (if (= @view-state :creating)
             "Creating..."
             "Create")]
          [:button.cancel
           {:type "button"
            :on-click cancel-fn}
           "Cancel"]]]))))
