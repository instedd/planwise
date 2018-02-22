(ns planwise.client.current-project.views
  (:require-macros [reagent.ratom :refer [reaction]])
  (:require [re-frame.core :refer [subscribe dispatch] :as rf]
            [re-com.core :as rc]
            [re-frame.utils :as c]
            [clojure.string :as str]
            [leaflet.core :refer [map-widget]]
            [planwise.client.utils :as utils]
            [planwise.client.mapping :as mapping]
            [planwise.client.asdf :as asdf]
            [planwise.client.config :as config]
            [planwise.client.styles :as styles]
            [planwise.client.components.common :as common]
            [planwise.client.current-project.api :as api]
            [planwise.client.effects :as effects]
            [planwise.client.current-project.db :as db]
            [planwise.client.current-project.components.header :refer [header-section]]
            [planwise.client.current-project.components.sidebar :refer [sidebar-section]]
            [planwise.client.current-project.components.sharing :refer [share-dialog]]))


(defn geojson-bbox-callback [dataset-id isochrones filters threshold]
  (fn [level bounds bbox-excluding callback]
    (let [level (js/parseInt level)
          bbox-excluding (map #(js/parseInt %) bbox-excluding)
          cache-existing (keys (get-in @isochrones [threshold level]))]
      ;; TODO: remove this and route the requests through re-frame
      (effects/make-api-request
       (assoc (api/fetch-isochrones-in-bbox
               @filters
               {:dataset-id dataset-id,
                :threshold threshold,
                :bbox bounds,
                :excluding (str/join "," cache-existing)
                :simplify (mapping/geojson-level->simplify level)})
              :on-success-cb (fn [response]
                               (rf/dispatch-sync [:current-project/isochrones-loaded response])
                               (let [isochrones-for-level (get-in @isochrones [threshold level])
                                     new-isochrones (->> response
                                                         (:facilities)
                                                         (map :id)
                                                         (remove (set bbox-excluding))
                                                         (select-keys isochrones-for-level)
                                                         (mapv (fn [[id isochrone]] {:id id, :isochrone isochrone})))]
                                 (callback (clj->js new-isochrones)))))))))

(defn marker-popup [{:keys [name type processing-status], :as marker}]
  (str
    "<b>" (utils/escape-html name) "</b>"
    "<br/>"
    (utils/escape-html type)
    (when (= "no-road-network" processing-status)
      (str
        "<br/>" "<br/>"
        "<i>" "This facility is too far from the closest road and it is not being evaluated for coverage." "</i>"))))

(def loading-wheel
  [:svg.circular {:viewBox "25 25 50 50"}
   [:circle.path {:cx 50
                  :cy 50
                  :fill "none"
                  :r 20
                  :stroke-miterlimit 10
                  :stroke-width 2}]])

(defn- project-tab [project-id project-dataset-id project-region-id project-region-admin-level selected-tab]
  (let [facilities-by-type (subscribe [:current-project/facilities-by-type])
        map-position (subscribe [:current-project/map-view :position])
        map-zoom (subscribe [:current-project/map-view :zoom])
        map-bbox (subscribe [:current-project/map-view :bbox])
        map-pixel-max-value (subscribe [:current-project/map-view :pixel-max-value])
        map-pixel-area (subscribe [:current-project/map-view :pixel-area-m2])
        map-key (subscribe [:current-project/map-key])
        map-geojson (subscribe [:current-project/map-geojson])
        map-state (subscribe [:current-project/map-state])
        marker-popup-fn marker-popup
        marker-style-fn #(when (= "no-road-network" (:processing-status %)) {:fillColor styles/invalid-facility-type})
        isochrones (subscribe [:current-project/facilities :isochrones])
        project-facilities-criteria (subscribe [:current-project/facilities-criteria])
        project-transport-time (subscribe [:current-project/transport-time])
        callback-fn (reaction (geojson-bbox-callback project-dataset-id isochrones project-facilities-criteria @project-transport-time))
        feature-fn #(aget % "isochrone")]

    (fn [project-id project-dataset-id project-region-id project-region-admin-level selected-tab]
      (dispatch [:current-project/tab-visited selected-tab])
      (case selected-tab
        (:demographics :facilities :transport)
        [:div
         [sidebar-section selected-tab]
         (when (= @map-state :loading-displayed)
           [:div.loading-indicator
            [:div.loading-wheel loading-wheel]
            [:div.loading-legend
             (case selected-tab
              :demographics "Loading demographics"
              :facilities "Retrieving facilities"
              :transport "Calculating coverage")]])
         [:div.map-container
          (let [map-props   {:position @map-position
                             :zoom @map-zoom
                             :min-zoom 5
                             :pixel-max-value @map-pixel-max-value
                             :pixel-area @map-pixel-area
                             :on-position-changed
                             #(dispatch [:current-project/update-position %])
                             :on-zoom-changed
                             #(dispatch [:current-project/update-zoom %])}

                layer-region-boundaries  (if @map-geojson
                                           [:geojson-layer {:data @map-geojson
                                                            :color styles/black
                                                            :fit-bounds true
                                                            :fillOpacity 0
                                                            :weight 2}])

                layer-demographics (let [population-map (mapping/region-map project-region-id)
                                         map-datafile   (if (and (mapping/calculate-demand-for-admin-level? project-region-admin-level)
                                                                 (= :transport selected-tab))
                                                          (when (asdf/valid? @map-key)
                                                            (if-let [key (asdf/value @map-key)]
                                                              (mapping/demand-map key)
                                                              population-map))
                                                          population-map)]
                                      [:wms-tile-layer {:url config/mapserver-url
                                                        :transparent true
                                                        :layers (when map-datafile mapping/layer-name)
                                                        :DATAFILE map-datafile
                                                        :opacity 0.6}])

                layers-facilities  (when (#{:facilities :transport} selected-tab)
                                     (for [[type facilities] @facilities-by-type]
                                       [:point-layer {:points facilities
                                                      :popup-fn marker-popup-fn
                                                      :style-fn marker-style-fn
                                                      :radius 4
                                                      :fillColor (:colour type)
                                                      :stroke false
                                                      :fillOpacity 1}]))

                layer-isochrones   (when (= :transport selected-tab)
                                      [:geojson-bbox-layer {:levels mapping/geojson-levels
                                                            :fillOpacity 1
                                                            :weight 2
                                                            :color styles/black
                                                            :group {:opacity 0.2}
                                                            :featureFn feature-fn
                                                            :callback @callback-fn}])

                map-layers (concat [mapping/bright-base-tile-layer
                                    layer-region-boundaries
                                    layer-demographics]
                                   layers-facilities
                                   [layer-isochrones])]

            (into [map-widget map-props] (filterv some? map-layers)))]]

        :scenarios
        [:div
         [:h1 "Scenarios"]]

        ;; default case
        nil))))

(defn- project-view []
  (let [page-params (subscribe [:page-params])
        current-project (subscribe [:current-project/current-data])
        project-shares (subscribe [:current-project/shares])
        view-state (subscribe [:current-project/view-state])
        wizard-mode-state (subscribe [:current-project/wizard-state])]
    (fn []
      (let [project-id (:id @page-params)
            selected-tab (:section @page-params)
            project-goal (:goal @current-project)
            project-dataset-id (:dataset-id @current-project)
            project-region-id (:region-id @current-project)
            project-region-admin-level (:region-admin-level @current-project)
            read-only (:read-only @current-project)
            share-count (count (asdf/value @project-shares))]
        [:article.project-view
         [header-section project-id project-goal selected-tab read-only share-count @wizard-mode-state]
         [project-tab project-id project-dataset-id project-region-id project-region-admin-level selected-tab]
         (when (db/show-share-dialog? @view-state)
           [common/modal-dialog {:on-backdrop-click
                                 #(dispatch [:current-project/close-share-dialog])}
             [share-dialog]])]))))


(defn project-page []
  (let [loaded? (subscribe [:current-project/loaded?])]
    (fn []
      (if @loaded?
        [project-view]
        [:article
         [common/loading-placeholder]]))))
