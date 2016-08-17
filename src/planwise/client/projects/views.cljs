(ns planwise.client.projects.views
  (:require-macros [reagent.ratom :refer [reaction]])
  (:require [re-frame.core :refer [subscribe dispatch dispatch-sync]]
            [re-com.core :as rc]
            [re-frame.utils :as c]
            [clojure.string :as str]
            [leaflet.core :refer [map-widget]]
            [planwise.client.mapping :as mapping]
            [planwise.client.config :as config]
            [planwise.client.styles :as styles]
            [planwise.client.projects.api :as api]
            [planwise.client.projects.db :as db]
            [planwise.client.components.common :as common]
            [planwise.client.projects.components.new-project
             :refer [new-project-dialog]]
            [planwise.client.projects.components.listing
             :refer [search-box no-projects-view projects-list]]
            [planwise.client.projects.components.header
             :refer [header-section]]
            [planwise.client.projects.components.sidebar
             :refer [sidebar-section]]))

(defn geojson-bbox-callback [isochrones filters threshold]
  (fn [level bounds bbox-excluding callback]
    (let [level (js/parseInt level)
          bbox-excluding (map #(js/parseInt %) bbox-excluding)
          cache-existing (keys (get-in @isochrones [threshold level]))]
      (api/fetch-isochrones-in-bbox
        @filters
        {:threshold threshold,
         :bbox bounds,
         :excluding (str/join "," cache-existing)
         :simplify (mapping/geojson-level->simplify level)}
        (fn [response]
          (dispatch-sync [:projects/isochrones-loaded response])
          (let [isochrones-for-level (get-in @isochrones [threshold level])
                new-isochrones (->> response
                                  (:facilities)
                                  (map :id)
                                  (remove (set bbox-excluding))
                                  (select-keys isochrones-for-level)
                                  (map (fn [[id isochrone]] {:id id, :isochrone isochrone}))
                                  (vec))]
            (callback (clj->js new-isochrones))))))))

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

(defn- project-tab [project-id project-region-id selected-tab]
  (let [facilities (subscribe [:projects/facilities :facilities])
        map-position (subscribe [:projects/map-view :position])
        map-zoom (subscribe [:projects/map-view :zoom])
        map-bbox (subscribe [:projects/map-view :bbox])
        demand-map-key (subscribe [:projects/demand-map-key])
        map-geojson (subscribe [:projects/map-geojson])
        marker-popup-fn #(str (:name %) "<br/>" (:type %))
        isochrones (subscribe [:projects/facilities :isochrones])
        project-facilities-criteria (subscribe [:projects/facilities-criteria])
        project-transport-time (subscribe [:projects/transport-time])
        callback-fn (reaction (geojson-bbox-callback isochrones project-facilities-criteria @project-transport-time))
        feature-fn #(aget % "isochrone")]

    (fn [project-id project-region-id selected-tab]
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
                mapping/gray-base-tile-layer
                ;; Markers with filtered facilities
                (when (#{:facilities :transport} selected-tab)
                  [:point-layer {:points @facilities
                                 :popup-fn marker-popup-fn
                                 :radius 5
                                 :color styles/light-grey
                                 :stroke false
                                 :fillOpacity 1}])
                ;; Demographics tile layer
                (let [demand-map     (when (= :transport selected-tab) (mapping/demand-map @demand-map-key))
                      population-map (mapping/region-map project-region-id)]
                  [:wms-tile-layer {:url config/mapserver-url
                                    :transparent true
                                    :layers mapping/layer-name
                                    :DATAFILE (or demand-map population-map)
                                    :opacity 0.3}])
                ;; Boundaries of working region
                (if @map-geojson
                  [:geojson-layer {:data @map-geojson
                                   :color styles/green
                                   :fit-bounds true
                                   :fillOpacity 0.1
                                   :weight 0}])
                ;; Isochrone for selected transport
                (when (= :transport selected-tab)
                  [:geojson-bbox-layer { :levels mapping/geojson-levels
                                         :fillOpacity 1
                                         :weight 2
                                         :color styles/green
                                         :group {:opacity 0.4}
                                         :featureFn feature-fn
                                         :callback @callback-fn}])])]]

        (= :scenarios selected-tab)
        [:div
         [:h1 "Scenarios"]]))))

(defn- project-view []
  (let [page-params (subscribe [:page-params])
        current-project (subscribe [:projects/current-data])]
    (fn []
      (let [project-id (:id @page-params)
            selected-tab (:section @page-params)
            project-goal (:goal @current-project)
            project-region-id (:region-id @current-project)]
        [:article.project-view
         [header-section project-id project-goal selected-tab]
         [project-tab project-id project-region-id selected-tab]]))))

(defn project-page []
  (let [view-state (subscribe [:projects/view-state])]
    (fn []
      (if (= @view-state :loading)
        [:article
         [common/loading-placeholder]]
        [project-view]))))
