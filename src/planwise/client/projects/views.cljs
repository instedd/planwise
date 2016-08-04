(ns planwise.client.projects.views
  (:require [re-frame.core :refer [subscribe dispatch]]
            [re-com.core :as rc]
            [clojure.string :as str]
            [leaflet.core :refer [map-widget]]
            [planwise.client.mapping :refer [gray-base-tile-layer]]
            [planwise.client.config :as config]
            [planwise.client.styles :as styles]
            [planwise.client.components.common :as common]
            [planwise.client.projects.components.new-project
             :refer [new-project-dialog]]
            [planwise.client.projects.components.listing
             :refer [search-box no-projects-view projects-list]]
            [planwise.client.projects.components.header
             :refer [header-section]]
            [planwise.client.projects.components.sidebar
             :refer [sidebar-section]]))

(defn project-list-page []
  (let [view-state (subscribe [:projects/view-state])
        projects (subscribe [:projects/list])
        filtered-projects (subscribe [:projects/filtered-list])]
    (fn []
      [:article.project-list
       [search-box (count @filtered-projects) (seq @projects)]
       (cond
         (nil? @projects) [common/loading-placeholder]
         (empty? @projects) [no-projects-view]
         :else [projects-list @filtered-projects])
       (when (or (= @view-state :creating) (= @view-state :create-dialog))
         [common/modal-dialog {:on-backdrop-click
                               #(dispatch [:projects/cancel-new-project])}
          [new-project-dialog]])])))

(defn- project-tab [project-id selected-tab]
  (let [facilities (subscribe [:projects/facilities :facilities])
        isochrones (subscribe [:projects/facilities :isochrones])
        map-position (subscribe [:projects/map-view :position])
        map-zoom (subscribe [:projects/map-view :zoom])
        map-bbox (subscribe [:projects/map-view :bbox])
        map-geojson (subscribe [:projects/map-geojson])]
    (fn [project-id selected-tab]
      (cond
        (#{:demographics
           :facilities
           :transport}
         selected-tab)
        [:div
         [sidebar-section selected-tab]
         [:div.map-container
          (->> [map-widget
                {:position @map-position
                 :zoom @map-zoom
                 :min-zoom 5
                 :on-position-changed
                 #(dispatch [:projects/update-position %])
                 :on-zoom-changed
                 #(dispatch [:projects/update-zoom %])}

                ;; Base tile layer
                gray-base-tile-layer
                ;; Markers with filtered facilities
                (when (#{:facilities :transport} selected-tab)
                  [:marker-layer {:points @facilities
                                  :icon-fn (constantly "circle-marker")
                                  :popup-fn #(str (:name %) "<br/>" (:type %))}])
                ;; Demographics tile layer
                [:tile-layer {:url config/demo-tile-url
                              :opacity 0.3}]
                ;; Boundaries of working region
                (if @map-geojson
                  [:geojson-layer {:data @map-geojson
                                   :color styles/green
                                   :fit-bounds true
                                   :fillOpacity 0.1
                                   :weight 0}])
                ;; Isochrone for selected transport
                (when (and (seq @isochrones) (= :transport selected-tab))
                  [:geojson-layer {:data @isochrones
                                   :fillOpacity 1
                                   :weight 2
                                   :color styles/green
                                   :group {:opacity 0.4}}])]

               ;; Filter out nils so leaflet/map-widget doesn't get confused
               (filter some?)
               vec)]]
        (= :scenarios selected-tab)
        [:div
         [:h1 "Scenarios"]]))))

(defn- project-view []
  (let [page-params (subscribe [:page-params])
        current-project (subscribe [:projects/current-data])]
    (fn []
      (let [project-id (:id @page-params)
            selected-tab (:section @page-params)
            project-goal (:goal @current-project)]
        [:article.project-view
         [header-section project-id project-goal selected-tab]
         [project-tab project-id selected-tab]]))))

(defn project-page []
  (let [view-state (subscribe [:projects/view-state])]
    (fn []
      (if (= @view-state :loading)
        [:article
         [common/loading-placeholder]]
        [project-view]))))
